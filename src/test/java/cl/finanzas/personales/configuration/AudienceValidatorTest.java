package cl.finanzas.personales.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 'issuer-uri' por si solo NO valida 'aud'. Estos tests fijan el comportamiento del validador que
 * hace que un access token de otra API del mismo tenant de Auth0 no entre.
 */
class AudienceValidatorTest {

    private static final String AUDIENCE_API = "https://api.finanzas-personales.dev";
    private static final String AUDIENCE_DE_OTRA_API = "https://api.otra-app.auth0.com";

    private final AudienceValidator validator = new AudienceValidator(AUDIENCE_API);

    @Test
    @DisplayName("acepta el token emitido para esta API")
    void aceptaElAudienceDeEstaApi() {
        OAuth2TokenValidatorResult resultado = validator.validate(tokenCon(AUDIENCE_API));

        assertThat(resultado.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("rechaza un token de otra API del mismo tenant")
    void rechazaElAudienceDeOtraApi() {
        OAuth2TokenValidatorResult resultado = validator.validate(tokenCon(AUDIENCE_DE_OTRA_API));

        assertThat(resultado.hasErrors()).isTrue();
        assertThat(resultado.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getErrorCode()).isEqualTo("invalid_token");
            assertThat(error.getDescription()).contains("aud");
        });
    }

    @Test
    @DisplayName("rechaza un token sin claim 'aud'")
    void rechazaUnTokenSinAudiencia() {
        Jwt sinAudience = tokenCon(List.of());

        OAuth2TokenValidatorResult resultado = validator.validate(sinAudience);

        assertThat(resultado.hasErrors()).isTrue();
        assertThat(resultado.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
    }

    @Test
    @DisplayName("acepta si uno de los varios audiences del token es el de esta API")
    void aceptaSiUnoDeLosVariosAudiencesEsElCorrecto() {
        Jwt token = tokenCon(AUDIENCE_DE_OTRA_API, AUDIENCE_API);

        assertThat(validator.validate(token).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("acepta cualquiera de los audiences configurados")
    void aceptaCualquieraDeLosAudiencesConfigurados() {
        AudienceValidator dosAudiences = new AudienceValidator(
                AUDIENCE_API,
                "https://api.finanzas-personales"
        );

        assertThat(dosAudiences.validate(tokenCon(AUDIENCE_API)).hasErrors()).isFalse();
        assertThat(dosAudiences.validate(tokenCon("https://api.finanzas-personales")).hasErrors()).isFalse();
        assertThat(dosAudiences.validate(tokenCon(AUDIENCE_DE_OTRA_API)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("falla al arrancar si no se configura ningun audience")
    void fallaSinAudiencesConfigurados() {
        assertThatThrownBy(() -> new AudienceValidator(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
    }

    private Jwt tokenCon(String... audiences) {
        return tokenCon(List.of(audiences));
    }

    private Jwt tokenCon(List<String> audiences) {
        Instant ahora = Instant.parse("2026-10-05T12:00:00Z");
        Jwt.Builder builder = Jwt.withTokenValue("token-falso")
                .header("alg", "RS256")
                .subject("auth0|abc123")
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(3600));

        return audiences.isEmpty() ? builder.build() : builder.audience(audiences).build();
    }
}