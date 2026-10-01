package cl.finanzas.personales.configuration;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Valida el claim 'aud' del access token.
 *
 * <p>Necesario porque {@code issuer-uri} por si solo NO mira la audiencia: valida firma,
 * issuer y expiracion, asi que un token legitimo emitido por el MISMO tenant de Auth0 para otra
 * API pasaria el filtro. Con este validador, cada API acepta solo los tokens que Pidieron para
 * ella.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    static final OAuth2Error AUDIENCE_INVALIDO = new OAuth2Error(
            "invalid_token",
            "El access token no fue emitido para esta API: el claim 'aud' no corresponde",
            null
    );

    private final Set<String> audiences;

    public AudienceValidator(String... audiences) {
        this(Arrays.asList(audiences));
    }

    public AudienceValidator(Collection<String> audiences) {
        if (audiences == null || audiences.isEmpty()) {
            throw new IllegalArgumentException("Hay que configurar al menos un audience (app.auth0.audiences)");
        }
        Set<String> aceptados = new LinkedHashSet<>();
        for (String audience : audiences) {
            if (audience != null && !audience.isBlank()) {
                aceptados.add(audience.trim());
            }
        }
        if (aceptados.isEmpty()) {
            throw new IllegalArgumentException("app.auth0.audiences no tiene ningun valor utilizable");
        }
        this.audiences = Collections.unmodifiableSet(aceptados);
    }

    Set<String> audiences() {
        return audiences;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audienciaDelToken = token.getAudience();

        if (audienciaDelToken == null || audienciaDelToken.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(AUDIENCE_INVALIDO);
        }

        boolean aceptada = audienciaDelToken.stream().anyMatch(audiences::contains);
        return aceptada
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(AUDIENCE_INVALIDO);
    }
}