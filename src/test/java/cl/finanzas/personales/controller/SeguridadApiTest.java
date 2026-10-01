package cl.finanzas.personales.controller;

import cl.finanzas.personales.configuration.SecurityConfiguration;
import cl.finanzas.personales.configuration.TimeConfiguration;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.service.CategoriaService;
import cl.finanzas.personales.service.CuentaService;
import cl.finanzas.personales.service.TransaccionService;
import cl.finanzas.personales.service.UsuarioActualService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin base de datos y sin Auth0: el JwtDecoder real se sustituye por uno que corre el MISMO
 * validador que usa la app (issuer + audience) pero sin verificar la firma, para poder probar el
 * rechazo por audience de punta a punta.
 */
@WebMvcTest(controllers = {
        UsuarioController.class,
        CuentaController.class,
        CategoriaController.class,
        TransaccionController.class,
        HealthController.class
})
@Import({SecurityConfiguration.class, TimeConfiguration.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://dev-tenant.auth0.com/",
        "app.auth0.audiences=https://api.finanzas-personales.dev"
})
class SeguridadApiTest {

    private static final String SUB = "auth0|abc123";
    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityConfiguration securityConfiguration;

    @MockitoBean
    private UsuarioActualService usuarioActualService;

    @MockitoBean
    private CuentaService cuentaService;

    @MockitoBean
    private CategoriaService categoriaService;

    @MockitoBean
    private TransaccionService transaccionService;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ---------------------------------------------------------------- 401 / 403

    @Test
    @DisplayName("sin token, un endpoint protegido responde 401 en JSON")
    void sinTokenResponde401EnJson() throws Exception {
        mockMvc.perform(get("/cuentas"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/cuentas"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractionsConLosServicios();
    }

    @Test
    @DisplayName("sin token, un path que ni existe tambien responde 401, no 404")
    void sinTokenNoSeEscapaConUn404() throws Exception {
        mockMvc.perform(get("/no-existe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("con token pero sin permisos, responde 403 en JSON")
    void conTokenYSinPermisoResponde403EnJson() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta no pertenece al usuario"))
                .given(cuentaService).eliminarCuenta(1L, USER_ID);
        given(usuarioActualService.idActual(any())).willReturn(USER_ID);

        mockMvc.perform(delete("/cuentas/1").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("La cuenta no pertenece al usuario"));
    }

    // ---------------------------------------------------------------- audience

    @Test
    @DisplayName("un token con el audience de otra API se rechaza antes de llegar al controller")
    void unTokenConAudienceIncorrectoNoEntra() throws Exception {
        decoderQueValida("https://api.otra-app.auth0.com");

        mockMvc.perform(get("/cuentas").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));

        verifyNoInteractionsConLosServicios();
    }

    @Test
    @DisplayName("un token con el audience de esta API si entra")
    void unTokenConElAudienceCorrectoEntra() throws Exception {
        decoderQueValida("https://api.finanzas-personales.dev");
        given(usuarioActualService.idActual(any())).willReturn(USER_ID);
        given(cuentaService.obtenerCuentasPorUsuario(USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/cuentas").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // ---------------------------------------------------------------- health publico

    @Test
    @DisplayName("/health y /health/db son los unicos endpoints publicos")
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/health/db"))
                .andExpect(status().isServiceUnavailable()); // sin DataSource real el check cae
    }

    // ---------------------------------------------------------------- identidad

    @Test
    @DisplayName("/usuarios/me devuelve el usuario del token")
    void usuariosMeDevuelveElUsuarioDelToken() throws Exception {
        given(usuarioActualService.usuarioActual(any())).willReturn(AppUser.builder()
                .id(USER_ID)
                .auth0Sub(SUB)
                .email("christian@example.com")
                .nombre("Christian Aguilar")
                .build());

        mockMvc.perform(get("/usuarios/me").with(jwt().jwt(token -> token
                        .subject(SUB)
                        .claim("email", "christian@example.com")
                        .claim("name", "Christian Aguilar"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value("christian@example.com"))
                .andExpect(jsonPath("$.nombre").value("Christian Aguilar"));
    }

    @Test
    @DisplayName("no queda ningun endpoint publico para crear usuarios")
    void noHayAltaDeUsuariosPorApi() throws Exception {
        mockMvc.perform(post("/usuarios").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()); // el endpoint ya no existe; sin token tampoco se filtra info
    }

    // ---------------------------------------------------------------- contrato sin userId

    @Test
    @DisplayName("las cuentas se piden sin userId: la identidad sale del token")
    void lasCuentasSePidenSinUserId() throws Exception {
        given(usuarioActualService.idActual(any())).willReturn(USER_ID);
        given(cuentaService.obtenerCuentasPorUsuario(USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/cuentas").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(cuentaService).obtenerCuentasPorUsuario(USER_ID);
    }

    @Test
    @DisplayName("una lista vacia es 200 con [], no 204 sin body")
    void unaListaVaciaEsDoscientosConArray() throws Exception {
        given(usuarioActualService.idActual(any())).willReturn(USER_ID);
        given(categoriaService.obtenerCategoriasPorUsuario(USER_ID)).willReturn(List.of());
        given(transaccionService.obtenerTransaccionesPorUsuario(any(), any(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/categorias").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("[]"));

        mockMvc.perform(get("/transacciones").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/hal+json"))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                // A diferencia de cuentas y categorias, aqui la respuesta no puede ser un []: es una
                // pagina HAL. Y RepresentationModel omite _embedded cuando va vacio, asi que sale
                // sin esa clave. El front lo cubre con su "?._embedded?.transaccionResponseList ?? []".
                .andExpect(jsonPath("$._embedded.transaccionResponseList").doesNotExist());
    }

    @Test
    @DisplayName("eliminar categoria y transaccion ya no pide userId en la query")
    void eliminarYaNoPideUserIdEnLaQuery() throws Exception {
        given(usuarioActualService.idActual(any())).willReturn(USER_ID);

        mockMvc.perform(post("/categorias/3/eliminar").with(jwt()))
                .andExpect(status().isNoContent());
        verify(categoriaService).eliminarCategoria(3L, USER_ID);

        mockMvc.perform(post("/transacciones/4/eliminar").with(jwt()))
                .andExpect(status().isNoContent());
        verify(transaccionService).eliminarTransaccion(4L, USER_ID);
    }

    /**
     * Reemplaza el JwtDecoder por uno que corre el validador de verdad de la app sobre un token
     * inventado: la firma no se verifica, pero el issuer y el audience si.
     */
    private void decoderQueValida(String audienceDelToken) {
        OAuth2TokenValidator<Jwt> validadorReal = securityConfiguration.jwtTokenValidator(
                "https://dev-tenant.auth0.com/",
                List.of("https://api.finanzas-personales.dev")
        );

        given(jwtDecoder.decode(any())).willAnswer(invocacion -> {
            Instant ahora = Instant.parse("2026-10-05T12:00:00Z");
            Jwt token = Jwt.withTokenValue(invocacion.getArgument(0))
                    .header("alg", "RS256")
                    .issuer("https://dev-tenant.auth0.com/")
                    .subject(SUB)
                    .audience(List.of(audienceDelToken))
                    .issuedAt(ahora)
                    .expiresAt(ahora.plusSeconds(3600))
                    .build();

            var resultado = validadorReal.validate(token);
            if (resultado.hasErrors()) {
                throw new JwtValidationException("El token no es valido", resultado.getErrors());
            }
            return token;
        });
    }

    private void verifyNoInteractionsConLosServicios() {
        // El request muere en el filtro de seguridad: ningun controller llega a correr.
        org.mockito.Mockito.verifyNoInteractions(usuarioActualService, cuentaService, categoriaService);
    }
}