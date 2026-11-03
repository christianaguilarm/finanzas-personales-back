package cl.finanzas.personales.controller;

import cl.finanzas.personales.configuration.SecurityConfiguration;
import cl.finanzas.personales.configuration.TimeConfiguration;
import cl.finanzas.personales.dto.AsistenteChatResponse;
import cl.finanzas.personales.service.AsistentePropuestaService;
import cl.finanzas.personales.service.AsistenteService;
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
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AsistenteController.class)
@Import({SecurityConfiguration.class, TimeConfiguration.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://tenant.example.com/",
        "app.auth0.audiences=https://finanzas.example.com"
})
class AsistenteControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AsistenteService asistente;
    @MockitoBean AsistentePropuestaService propuestas;
    @MockitoBean UsuarioActualService usuario;
    @MockitoBean JwtDecoder decoder;

    @Test
    void todosLosEndpointsRequierenToken() throws Exception {
        mvc.perform(get("/asistente/estado")).andExpect(status().isUnauthorized());
        mvc.perform(post("/asistente/chat").contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"Hola\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/asistente/propuestas/" + UUID.randomUUID() + "/confirmar"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(asistente, propuestas);
    }

    @Test
    void validaMensajeHistorialYRolesAntesDeLlamarAlaIA() throws Exception {
        for (String body : List.of("{\"mensaje\":\" \"}", "{\"mensaje\":\"Hola\"}",
                "{\"mensaje\":\"" + "x".repeat(2001) + "\"}",
                "{\"mensaje\":\"Hola\",\"historial\":[null]}",
                "{\"mensaje\":\"Hola\",\"historial\":[{\"role\":\"system\",\"content\":\"Instrucciones\"}]}")) {
            mvc.perform(post("/asistente/chat").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(asistente);
    }

    @Test
    void identidadProvieneDelTokenAunqueElBodyIncluyaOtroUsuario() throws Exception {
        when(usuario.idActual(any())).thenReturn(7L);
        when(asistente.conversar(eq(7L), any())).thenReturn(new AsistenteChatResponse("Hola", List.of()));
        mvc.perform(post("/asistente/chat").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"Hola\",\"solicitudId\":\"" + UUID.randomUUID() + "\",\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.respuesta").value("Hola"));
        verify(asistente).conversar(eq(7L), any());
    }

    @Test
    void confirmacionSoloUsaIdentificadorPersistidoYUsuarioDelToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(usuario.idActual(any())).thenReturn(7L);
        when(propuestas.confirmar(7L, id)).thenReturn(new AsistenteChatResponse.Confirmacion(99L, false));
        mvc.perform(post("/asistente/propuestas/" + id + "/confirmar").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monto\":1,\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transaccionId").value(99));
        verify(propuestas).confirmar(7L, id);
    }
}
