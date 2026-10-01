package cl.finanzas.personales.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests sobre las piezas reales de SecurityConfiguration, sin levantar Spring ni tocar la red:
 * el JwtDecoder que arranca contra el JWKS de Auth0 no se puede construir en un test, pero el
 * validador que decide si un token entra si.
 */
class SecurityConfigurationTest {

    private static final String ISSUER = "https://dev-tenant.auth0.com/";
    private static final String AUDIENCE_API = "https://api.finanzas-personales.dev";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);
    private static final Instant ahora = RELOJ.instant();

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    /** El mismo validador que inyecta la app: issuer + audience, en paralelo. */
    private final OAuth2TokenValidator<Jwt> validador =
            configuration.jwtTokenValidator(ISSUER, List.of(AUDIENCE_API));

    @Test
    @DisplayName("acepta un token del tenant correcto con el audience de esta API")
    void aceptaUnTokenValido() {
        assertThat(validador.validate(token(ISSUER, AUDIENCE_API, ahora, ahora.plusSeconds(3600))).hasErrors())
                .isFalse();
    }

    @Test
    @DisplayName("rechaza un token con el audience de otra API aunque venga del mismo tenant")
    void rechazaUnAudienceIncorrecto() {
        var resultado = validador.validate(token(ISSUER, "https://api.otra-app.auth0.com", ahora, ahora.plusSeconds(3600)));

        assertThat(resultado.hasErrors()).isTrue();
        assertThat(resultado.getErrors()).anySatisfy(error ->
                assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
    }

    @Test
    @DisplayName("rechaza un token de otro tenant aunque traiga el audience correcto")
    void rechazaUnIssuerIncorrecto() {
        var resultado = validador.validate(
                token("https://otro-tenant.auth0.com/", AUDIENCE_API, ahora, ahora.plusSeconds(3600)));

        assertThat(resultado.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("rechaza un token expirado")
    void rechazaUnTokenExpirado() {
        // El validador de expiracion usa el reloj real, no el RELOJ fijo de este test.
        Instant ahoraReal = Instant.now();

        var resultado = validador.validate(
                token(ISSUER, AUDIENCE_API, ahoraReal.minusSeconds(7200), ahoraReal.minusSeconds(3600)));

        assertThat(resultado.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("el entry point responde 401 en JSON, no con la pagina de error de Spring")
    void elEntryPointEscribeUn401EnJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cuentas");
        MockHttpServletResponse response = new MockHttpServletResponse();

        configuration.authenticationEntryPoint(objectMapper(), RELOJ)
                .commence(request, response, new AuthenticationException("sin token") {});

        Map<String, Object> body = cuerpo(response);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(body).containsEntry("status", 401)
                .containsEntry("error", "Unauthorized")
                .containsEntry("path", "/api/cuentas");
        assertThat(body.get("message")).asString().isNotBlank();
    }

    @Test
    @DisplayName("el access denied handler responde 403 en JSON")
    void elAccessDeniedHandlerEscribeUn403EnJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/cuentas/7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        configuration.accessDeniedHandler(objectMapper(), RELOJ)
                .handle(request, response, new AccessDeniedException("no es tuya"));

        Map<String, Object> body = cuerpo(response);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body).containsEntry("status", 403)
                .containsEntry("error", "Forbidden")
                .containsEntry("path", "/api/cuentas/7");
    }

    @Test
    @DisplayName("los dos errores usan el mismo formato que ApiExceptionHandler")
    void elFormatoCoincideConApiExceptionHandler() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        configuration.accessDeniedHandler(objectMapper(), RELOJ)
                .handle(new MockHttpServletRequest("GET", "/api/transacciones"), response,
                        new AccessDeniedException("x"));

        assertThat(cuerpo(response).keySet()).containsExactlyInAnyOrder(
                "timestamp", "status", "error", "path", "message"
        );
    }

    @Test
    @DisplayName("CORS: el dominio de produccion de Vercel esta permitido, con y sin prefijo de preview")
    void corsPermiteLosDominiosDeVercel() {
        var patrones = patterns();

        assertThat(patrones).contains(
                "https://finanzas-personales-ui.vercel.app",
                "https://finanzas-personales-ui-*.vercel.app"
        );
    }

    @Test
    @DisplayName("CORS: el wildcard de preview NO reemplaza al dominio de produccion")
    void corsElWildcardNoCubreProduccion() {
        // El wildcard exige un guion antes del asterisco, asi que por si solo rechazaria
        // 'https://finanzas-personales-ui.vercel.app' y el navegador bloquearia todo en
        // produccion. Por eso la URL base esta explicita en la lista.
        var config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("https://finanzas-personales-ui-*.vercel.app"));

        assertThat(config.checkOrigin("https://finanzas-personales-ui.vercel.app")).isNull();
        assertThat(config.checkOrigin("https://finanzas-personales-ui-abc123.vercel.app"))
                .isEqualTo("https://finanzas-personales-ui-abc123.vercel.app");
    }

    @Test
    @DisplayName("CORS: localhost en dev y origenes ajenos quedan fuera")
    void corsNoAbreDeMas() {
        var config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOriginPatterns(patterns());

        assertThat(config.checkOrigin("http://localhost:4200"))
                .isEqualTo("http://localhost:4200");
        assertThat(config.checkOrigin("https://otro-proyecto.vercel.app")).isNull();
        // El comodin no debe abrir la puerta a un sufijo tipo '.evil.com'.
        assertThat(config.checkOrigin("https://finanzas-personales-ui.vercel.app.evil.com")).isNull();    }

    @SuppressWarnings("unchecked")
    private List<String> patterns() {
        var source = (org.springframework.web.cors.UrlBasedCorsConfigurationSource)
                configuration.corsConfigurationSource();
        var config = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/cuentas"));

        return List.copyOf(config.getAllowedOriginPatterns());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cuerpo(MockHttpServletResponse response) throws Exception {
        return objectMapper().readValue(response.getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    /** El mismo ObjectMapper que arma Spring Boot: con modulo de tiempo y fechas en ISO. */
    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private Jwt token(String issuer, String audience, Instant issuedAt, Instant expiresAt) {
        return Jwt.withTokenValue("token-falso")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("auth0|abc123")
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }
}