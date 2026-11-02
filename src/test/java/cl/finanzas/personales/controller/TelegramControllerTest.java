package cl.finanzas.personales.controller;

import cl.finanzas.personales.configuration.SecurityConfiguration;
import cl.finanzas.personales.configuration.TimeConfiguration;
import cl.finanzas.personales.dto.TelegramResponse;
import cl.finanzas.personales.service.UsuarioActualService;
import cl.finanzas.personales.service.telegram.TelegramColaService;
import cl.finanzas.personales.service.telegram.TelegramConexionService;
import cl.finanzas.personales.service.telegram.TelegramPreferenciasService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TelegramController.class)
@Import({SecurityConfiguration.class, TimeConfiguration.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://tenant.example.com/",
        "app.auth0.audiences=https://finanzas.example.com"
})
class TelegramControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TelegramConexionService conexiones;
    @MockitoBean TelegramColaService cola;
    @MockitoBean UsuarioActualService usuario;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean TelegramPreferenciasService preferencias;

    @Test
    void gestionarElVinculoRequiereAuth0() throws Exception {
        mvc.perform(get("/telegram/estado")).andExpect(status().isUnauthorized());
        mvc.perform(post("/telegram/vincular")).andExpect(status().isUnauthorized());
        mvc.perform(post("/telegram/desvincular")).andExpect(status().isUnauthorized());
        mvc.perform(get("/telegram/preferencias")).andExpect(status().isUnauthorized());
        mvc.perform(put("/telegram/preferencias").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(conexiones, cola);
    }

    @Test
    void preferenciasUsanLaIdentidadDelTokenYValidanIdsPositivos() throws Exception {
        when(usuario.idActual(any())).thenReturn(7L);
        var valores = new cl.finanzas.personales.dto.TelegramPreferencias(2L, 10L);
        when(preferencias.guardar(7L, valores)).thenReturn(valores);
        mvc.perform(put("/telegram/preferencias").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuentaId\":2,\"categoriaId\":10,\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.cuentaId").value(2));
        verify(preferencias).guardar(7L, valores);
        mvc.perform(put("/telegram/preferencias").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuentaId\":0,\"categoriaId\":-1}"))
                .andExpect(status().isBadRequest());
        verify(preferencias, times(1)).guardar(anyLong(), any());
    }

    @Test
    void soloElPostDelWebhookEstaExentoDeJwtYDelegaLaValidacionDelSecreto() throws Exception {
        mvc.perform(post("/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Bot-Api-Secret-Token", "secreto-prueba").content("{\"update_id\":1}"))
                .andExpect(status().isOk());
        verify(cola).recibir(eq("secreto-prueba"), any());
        mvc.perform(get("/telegram/webhook")).andExpect(status().isUnauthorized());
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook no autorizado"))
                .when(cola).recibir(isNull(), any());
        mvc.perform(post("/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elEnlaceUsaElUsuarioDelTokenYNoSePuedeCachear() throws Exception {
        when(usuario.idActual(any())).thenReturn(7L);
        when(conexiones.crearEnlace(7L)).thenReturn(new TelegramResponse.Enlace(
                "https://t.me/prueba_bot?start=codigo", Instant.parse("2026-11-01T18:10:00Z")));
        mvc.perform(post("/telegram/vincular").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.url").value("https://t.me/prueba_bot?start=codigo"));
        verify(conexiones).crearEnlace(7L);
    }

    @Test
    void desvincularNoAceptaUnaIdentidadElegidaPorElCliente() throws Exception {
        when(usuario.idActual(any())).thenReturn(7L);
        mvc.perform(post("/telegram/desvincular").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999}"))
                .andExpect(status().isNoContent());
        verify(conexiones).desvincular(7L);
    }
}
