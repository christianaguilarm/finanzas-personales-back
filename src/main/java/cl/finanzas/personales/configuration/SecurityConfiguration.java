package cl.finanzas.personales.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 'issuer-uri' no alcanza: hay que validar 'aud' a mano, o un token de otra API del mismo
     * tenant de Auth0 entraria aqui. El validador por defecto (firma, iss, exp) se combina con
     * el de audiencia mediante DelegatingOAuth2TokenValidator.
     */
    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${app.auth0.audiences}") List<String> audiences
    ) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audiences)
        );
    }

    @Bean
    public JwtDecoder jwtDecoder(
            OAuth2TokenValidator<Jwt> jwtTokenValidator,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        decoder.setJwtValidator(jwtTokenValidator);
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable) // bearer tokens: no hay sesion ni cookies
                .authorizeHttpRequests(auth -> auth
                        // Ojo: hay server.servlet.context-path=/api, asi que los matchers van
                        // SIN el prefijo. Un /api/** aqui no matchearia nada.
                        .requestMatchers("/health", "/health/db").permitAll()
                        .requestMatchers("/error").permitAll() // que el dispatch de error no se
                                                               // convierta en un 401 tapando el
                                                               // status real (404, 500, ...)
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        // Sin esto, un token invalido (issuer, audience, firma o expiracion) lo
                        // rechaza el BearerTokenAuthenticationFilter con su propio entry point y la
                        // respuesta sale por sendError: un 401 con otra forma, no este JSON.
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // Sin esto, una peticion sin cabecera Authorization caeria en el entry point por
                // defecto de Spring y responderia 403 sin body en vez de un 401 con JSON.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper, Clock clock) {
        return (request, response, authException) -> {
            // RFC 6750: un 401 debe indicar como autenticarse.
            StringBuilder wwwAuthenticate = new StringBuilder("Bearer");
            if (authException instanceof OAuth2AuthenticationException oauth2
                    && oauth2.getError() != null) {
                wwwAuthenticate.append(" error=\"").append(oauth2.getError().getErrorCode()).append('"');
            }
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate.toString());

            escribirError(
                    request.getRequestURI(),
                    response,
                    objectMapper,
                    clock,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Falta un access token valido de Auth0"
            );
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper, Clock clock) {
        return (request, response, accessDeniedException) -> escribirError(
                request.getRequestURI(),
                response,
                objectMapper,
                clock,
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "El token no da acceso a este recurso"
        );
    }

    /**
     * 401 y 403 en el mismo formato que ApiExceptionHandler, para que el front no tenga que
     * distinguir entre la pagina de error de Spring y un JSON.
     */
    private void escribirError(
            String path,
            HttpServletResponse response,
            ObjectMapper objectMapper,
            Clock clock,
            HttpStatus status,
            String error,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now(clock));
        body.put("status", status.value());
        body.put("error", error);
        body.put("path", path);
        body.put("message", message);

        objectMapper.writeValue(response.getWriter(), body);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // El dominio de produccion va explicito, y el wildcard cubre los subdominios de
        // preview que Vercel genera en cada deploy. El wildcard NO alcanza para produccion:
        // 'https://finanzas-personales-ui-*.vercel.app' exige un guion antes del asterisco,
        // asi que no matchea 'https://finanzas-personales-ui.vercel.app' (verificado con
        // CorsConfiguration.checkOrigin). Por eso la URL base esta en su propia linea.
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:4200",
                "http://localhost:63080",
                "https://finanzas-personales-ui.vercel.app",
                "https://finanzas-personales-ui-*.vercel.app"
        ));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));

        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}