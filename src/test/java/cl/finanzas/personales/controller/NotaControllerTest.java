package cl.finanzas.personales.controller;

import cl.finanzas.personales.configuration.SecurityConfiguration;
import cl.finanzas.personales.configuration.TimeConfiguration;
import cl.finanzas.personales.dto.NotaResponse;
import cl.finanzas.personales.service.NotaService;
import cl.finanzas.personales.service.UsuarioActualService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotaController.class)
@Import({SecurityConfiguration.class, TimeConfiguration.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://tenant.example.com/",
        "app.auth0.audiences=https://finanzas.example.com"
})
class NotaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean NotaService notas;
    @MockitoBean UsuarioActualService usuario;
    @MockitoBean JwtDecoder decoder;

    @Test
    void exigeTokenEnTodosLosMetodos() throws Exception {
        mvc.perform(get("/notas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/notas/proximas")).andExpect(status().isUnauthorized());
        mvc.perform(post("/notas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/notas/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/notas/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notas);
    }

    @Test
    void usaIdentidadDelTokenYDevuelveListaVaciaConCuerpo() throws Exception {
        when(usuario.idActual(any())).thenReturn(7L);
        when(notas.listar(7L)).thenReturn(List.of());
        mvc.perform(get("/notas").with(jwt())).andExpect(status().isOk()).andExpect(content().json("[]"));
        when(notas.proximas(7L)).thenReturn(List.of(new NotaResponse(1L, "Pagar luz", LocalDate.of(2026, 11, 5), false, 2L)));
        mvc.perform(get("/notas/proximas").with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].diasRestantes").value(2));
        verify(notas).proximas(7L);
    }

    @Test
    void validaTextoAntesDeGuardar() throws Exception {
        mvc.perform(post("/notas").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"   \",\"fechaLimite\":\"2026-11-05\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notas);
    }
}
