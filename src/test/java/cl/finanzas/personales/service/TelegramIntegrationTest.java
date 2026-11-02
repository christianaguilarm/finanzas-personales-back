package cl.finanzas.personales.service;

import cl.finanzas.personales.configuration.AsistenteProperties;
import cl.finanzas.personales.configuration.TelegramProperties;
import cl.finanzas.personales.dto.AsistenteChatResponse;
import cl.finanzas.personales.dto.TransaccionRequest;
import cl.finanzas.personales.dto.TransaccionResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.PropuestaAsistente;
import cl.finanzas.personales.repository.*;
import cl.finanzas.personales.service.telegram.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "app.telegram.bot-token=123456:token-de-prueba",
        "app.telegram.bot-username=finanzas_pruebas_bot",
        "app.telegram.webhook-secret=secreto-de-webhook-solo-pruebas"
})
@Import({TelegramIntegrationTest.Config.class, TelegramConexionService.class, TelegramBotService.class,
        TelegramColaService.class, TelegramRegistro.class, AsistentePropuestaService.class, TelegramPreferenciasService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TelegramIntegrationTest {
    private static final Instant AHORA = Instant.parse("2026-11-01T18:00:00Z");
    @Autowired TelegramConexionService vinculacion;
    @Autowired TelegramColaService cola;
    @Autowired TelegramConexionRepository conexiones;
    @Autowired TelegramUpdateRepository updates;
    @Autowired TelegramPropuestaRepository propuestasTelegram;
    @Autowired PropuestaAsistenteRepository propuestas;
    @Autowired AppUserRepository usuarios;
    @Autowired ObjectMapper mapper;
    @Autowired TelegramProperties properties;
    @Autowired TelegramPreferenciasService preferencias;
    @Autowired AsistentePropuestaService propuestasService;
    @MockitoBean AsistenteService asistente;
    @MockitoBean TelegramApiClient api;
    @MockitoBean TransaccionService transacciones;
    @MockitoBean CuentaService cuentas;
    @MockitoBean CategoriaService categorias;
    private AppUser usuario;

    @TestConfiguration
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean Clock clock() { return Clock.fixed(AHORA, ZoneOffset.UTC); }
        @Bean Validator validator() { return Validation.buildDefaultValidatorFactory().getValidator(); }
        @Bean AsistenteProperties asistenteProperties() { return new AsistenteProperties(); }
        @Bean TelegramProperties telegramProperties() {
            var p = new TelegramProperties();
            p.setBotToken("123456:token-de-prueba");
            p.setBotUsername("finanzas_pruebas_bot");
            p.setWebhookSecret("secreto-de-webhook-solo-pruebas");
            return p;
        }
    }

    @BeforeEach
    void configurar() {
        usuario = usuario("usuario@example.com");
        when(asistente.conversarTelegram(anyLong(), any())).thenReturn(new AsistenteChatResponse("Respuesta real", List.of()));
    }

    @AfterEach
    void limpiar() {
        propuestasTelegram.deleteAll();
        updates.deleteAll();
        conexiones.deleteAll();
        propuestas.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    void unaPreferenciaInvalidaPermitePedirOtraOpcionSinInvalidarLaRespuestaDelBot() throws Exception {
        conectar(usuario, 123L);
        var conexion = conexiones.findById(usuario.getId()).orElseThrow();
        conexion.setCuentaPredeterminadaId(99999999L);
        conexiones.saveAndFlush(conexion);
        var ia = mock(AsistenteIaClient.class);
        var respuestaTool = mapper.createObjectNode().put("role", "assistant");
        respuestaTool.putArray("tool_calls").addObject().put("id", "call_1").put("type", "function")
                .putObject("function").put("name", "preparar_movimiento")
                .put("arguments", "{\"tipo\":\"EGRESO\",\"fecha\":\"2026-11-01\",\"monto\":5000,\"medio\":\"DEBITO\"}");
        when(ia.conversar(anyList(), any())).thenReturn(respuestaTool,
                mapper.createObjectNode().put("role", "assistant").put("content", "Elige otra cuenta para este gasto."));
        var propertiesIA = new AsistenteProperties(); propertiesIA.setApiKey("clave-solo-pruebas");
        var agente = new AsistenteService(propertiesIA, ia, propuestasService, cuentas, categorias, transacciones,
                mock(TransaccionRepository.class), mapper, Clock.fixed(AHORA, ZoneOffset.UTC), preferencias);
        when(asistente.conversarTelegram(eq(usuario.getId()), any())).thenAnswer(i -> agente.conversarTelegram(i.getArgument(0), i.getArgument(1)));
        recibir(mensaje(1, 123));
        cola.procesarSiguiente();
        assertTrue(updates.findById(1L).orElseThrow().getRespuestas().contains("Elige otra cuenta"));
        assertEquals(0, propuestas.count());
        verifyNoInteractions(transacciones);
    }

    @Test
    void desconectarYReconectarConservaLasPreferencias() {
        conectar(usuario, 123L);
        var c = conexiones.findById(usuario.getId()).orElseThrow();
        c.setCuentaPredeterminadaId(20L); c.setCategoriaPredeterminadaId(30L);
        conexiones.saveAndFlush(c);
        vinculacion.desvincular(usuario.getId());
        conectar(usuario, 123L);
        var reconectada = conexiones.findById(usuario.getId()).orElseThrow();
        assertEquals(20L, reconectada.getCuentaPredeterminadaId());
        assertEquals(30L, reconectada.getCategoriaPredeterminadaId());
    }

    @Test
    void codigoEsTemporalSeGuardaHasheadoYSeConsumeUnaSolaVez() {
        var enlace = vinculacion.crearEnlace(usuario.getId());
        String codigo = codigo(enlace.url());
        var conexion = conexiones.findById(usuario.getId()).orElseThrow();
        assertNotEquals(codigo, conexion.getCodigoHash());
        assertEquals(64, conexion.getCodigoHash().length());
        assertEquals(AHORA.plusSeconds(600), enlace.expiraEn());
        vinculacion.vincular(codigo, 123L, 123L, "persona");
        assertTrue(vinculacion.estado(usuario.getId()).vinculado());
        assertNull(conexiones.findById(usuario.getId()).orElseThrow().getCodigoHash());
        assertThrows(ResponseStatusException.class, () -> vinculacion.vincular(codigo, 456L, 456L, "otro"));
    }

    @Test
    void regenerarInvalidaElEnlaceAnteriorYUnEnlaceVencidoNoConecta() {
        String anterior = codigo(vinculacion.crearEnlace(usuario.getId()).url());
        String nuevo = codigo(vinculacion.crearEnlace(usuario.getId()).url());
        assertThrows(ResponseStatusException.class, () -> vinculacion.vincular(anterior, 123L, 123L, null));
        var c = conexiones.findById(usuario.getId()).orElseThrow();
        c.setCodigoExpiraEn(AHORA);
        conexiones.saveAndFlush(c);
        assertThrows(ResponseStatusException.class, () -> vinculacion.vincular(nuevo, 123L, 123L, null));
        assertFalse(vinculacion.estado(usuario.getId()).vinculado());
    }

    @Test
    void mismoTelegramNoPuedeConectarseAOtraCuenta() {
        conectar(usuario, 123L);
        var otro = usuario("otro@example.com");
        String codigo = codigo(vinculacion.crearEnlace(otro.getId()).url());
        assertThrows(ResponseStatusException.class, () -> vinculacion.vincular(codigo, 123L, 123L, "otro"));
        assertEquals(usuario.getId(), conexiones.findByTelegramUserId(123L).orElseThrow().getUserId());
        assertFalse(vinculacion.estado(otro.getId()).vinculado());
    }

    @Test
    void secretoInvalidoNoRecibeNiProcesaActualizaciones() {
        var error = assertThrows(ResponseStatusException.class, () -> cola.recibir("incorrecto", mensaje(1, 123)));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        assertEquals(0, updates.count());
        verifyNoInteractions(asistente, api);
    }

    @Test
    void ignoraGruposYNoExponeDatosAUsuariosSinVincular() {
        JsonNode grupo = mensaje(1, 123);
        ((com.fasterxml.jackson.databind.node.ObjectNode) grupo.path("message").path("chat")).put("type", "group");
        recibir(grupo);
        assertEquals(0, updates.count());
        recibir(mensaje(2, 456));
        assertTrue(cola.procesarSiguiente());
        assertTrue(updates.findById(2L).orElseThrow().getRespuestas().contains("Conecta tu cuenta"));
        verifyNoInteractions(asistente, transacciones);
    }

    @Test
    void webhookEsRapidoYDuplicadosNoReejecutanNiReinicianElEvento() {
        conectar(usuario, 123L);
        recibir(mensaje(1, 123));
        verifyNoInteractions(asistente, api);
        cola.procesarSiguiente();
        var procesado = updates.findById(1L).orElseThrow();
        assertTrue(procesado.isProcesado());
        assertEquals("{}", procesado.getContenido());
        recibir(mensaje(1, 123));
        assertFalse(cola.procesarSiguiente());
        verify(asistente, times(1)).conversarTelegram(eq(usuario.getId()), any());
        assertEquals(1, updates.count());
        assertTrue(updates.findById(1L).orElseThrow().isProcesado());
    }

    @Test
    void vinculaDesdeStartYLosMensajesUsanContextoDelUsuarioCorrecto() {
        String codigo = codigo(vinculacion.crearEnlace(usuario.getId()).url());
        JsonNode inicio = mensaje(1, 123);
        ((com.fasterxml.jackson.databind.node.ObjectNode) inicio.path("message")).put("text", "/start " + codigo);
        recibir(inicio);
        cola.procesarSiguiente();
        assertTrue(vinculacion.estado(usuario.getId()).vinculado());
        recibir(mensaje(2, 123));
        cola.procesarSiguiente();
        recibir(mensaje(3, 123));
        cola.procesarSiguiente();
        var captor = org.mockito.ArgumentCaptor.forClass(cl.finanzas.personales.dto.AsistenteChatRequest.class);
        verify(asistente, times(2)).conversarTelegram(eq(usuario.getId()), captor.capture());
        assertTrue(captor.getAllValues().get(0).historial().isEmpty());
        assertEquals(2, captor.getAllValues().get(1).historial().size());
    }

    @Test
    void confirmaLaPropuestaDosVecesSinDuplicarElMovimiento() throws Exception {
        UUID id = prepararPropuesta();
        when(transacciones.crearTransaccion(eq(usuario.getId()), any())).thenReturn(mapper.readValue("{\"id\":99}", TransaccionResponse.class));
        recibir(callback(2, 123, "confirmar:" + id));
        cola.procesarSiguiente();
        recibir(callback(3, 123, "confirmar:" + id));
        cola.procesarSiguiente();
        verify(transacciones, times(1)).crearTransaccion(eq(usuario.getId()), any());
        assertTrue(updates.findById(3L).orElseThrow().getRespuestas().contains("ya estaba guardado"));
    }

    @Test
    void otroUsuarioYUnaPropuestaDescartadaNoPuedenCrearMovimientos() throws Exception {
        UUID id = prepararPropuesta();
        var otro = usuario("otro@example.com");
        conectar(otro, 456L);
        recibir(callback(2, 456, "confirmar:" + id));
        cola.procesarSiguiente();
        assertTrue(updates.findById(2L).orElseThrow().getRespuestas().contains("no pertenece"));
        recibir(callback(3, 123, "descartar:" + id));
        cola.procesarSiguiente();
        recibir(callback(4, 123, "confirmar:" + id));
        cola.procesarSiguiente();
        assertTrue(updates.findById(4L).orElseThrow().getRespuestas().contains("descartada"));
        verifyNoInteractions(transacciones);
    }

    @Test
    void desconectarInvalidaBotonesAnterioresInclusoSiSeReconectaElMismoTelegram() throws Exception {
        UUID id = prepararPropuesta();
        vinculacion.desvincular(usuario.getId());
        conectar(usuario, 123L);
        recibir(callback(2, 123, "confirmar:" + id));
        cola.procesarSiguiente();
        assertTrue(updates.findById(2L).orElseThrow().getRespuestas().contains("no pertenece"));
        verifyNoInteractions(transacciones);
    }

    @Test
    void noEntregaRespuestasFinancierasPendientesDespuesDeDesconectar() {
        conectar(usuario, 123L);
        recibir(mensaje(1, 123));
        cola.procesarSiguiente();
        vinculacion.desvincular(usuario.getId());
        assertTrue(cola.entregarSiguiente());
        assertTrue(updates.findById(1L).orElseThrow().isEntregado());
        assertEquals("[]", updates.findById(1L).orElseThrow().getRespuestas());
        verifyNoInteractions(api);
    }

    @Test
    void puedeDesconectarDuranteLaConsultaSinEsperarAlaIA() throws Exception {
        conectar(usuario, 123L);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        when(asistente.conversarTelegram(eq(usuario.getId()), any())).thenAnswer(invocacion -> {
            executor.submit(() -> vinculacion.desvincular(usuario.getId())).get(5, java.util.concurrent.TimeUnit.SECONDS);
            return new AsistenteChatResponse("Respuesta financiera que no debe entregarse", List.of());
        });
        try {
            recibir(mensaje(1, 123));
            cola.procesarSiguiente();
            assertFalse(vinculacion.estado(usuario.getId()).vinculado());
            cola.entregarSiguiente();
            verifyNoInteractions(api);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void reintentaSoloLaEntregaSinVolverAConsultarAlaIA() {
        conectar(usuario, 123L);
        recibir(mensaje(1, 123));
        cola.procesarSiguiente();
        doThrow(new TelegramApiClient.TelegramApiException()).when(api).enviar(anyString(), any());
        assertFalse(cola.entregarSiguiente());
        assertEquals(1, updates.findById(1L).orElseThrow().getFallos());
        assertFalse(cola.entregarSiguiente());
        var evento = updates.findById(1L).orElseThrow();
        evento.setProximoIntento(AHORA);
        updates.saveAndFlush(evento);
        doNothing().when(api).enviar(anyString(), any());
        assertTrue(cola.entregarSiguiente());
        assertTrue(updates.findById(1L).orElseThrow().isEntregado());
        verify(asistente, times(1)).conversarTelegram(eq(usuario.getId()), any());
    }

    @Test
    void errorDeNegocioRevierteLaConfirmacionYDevuelveUnAviso() throws Exception {
        UUID id = prepararPropuesta();
        when(transacciones.crearTransaccion(eq(usuario.getId()), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta inactiva"));
        recibir(callback(2, 123, "confirmar:" + id));
        cola.procesarSiguiente();
        assertNull(propuestas.findById(id).orElseThrow().getTransaccionId());
        assertTrue(updates.findById(2L).orElseThrow().getRespuestas().contains("Cuenta inactiva"));
    }

    private UUID prepararPropuesta() throws Exception {
        conectar(usuario, 123L);
        var request = mapper.readValue("{\"cuentaId\":1,\"tipo\":\"EGRESO\",\"fecha\":\"2026-11-01\",\"monto\":15000,\"medio\":\"DEBITO\"}", TransaccionRequest.class);
        var p = new PropuestaAsistente();
        p.setId(UUID.randomUUID());
        p.setUserId(usuario.getId());
        p.setContenido(mapper.writeValueAsString(request));
        p.setExpiraEn(AHORA.plusSeconds(1800));
        propuestas.saveAndFlush(p);
        when(asistente.conversarTelegram(eq(usuario.getId()), any())).thenReturn(new AsistenteChatResponse("Revisa la propuesta", List.of(
                new AsistenteChatResponse.Propuesta(p.getId(), p.getExpiraEn(), request, "Cuenta", "Bencina"))));
        recibir(mensaje(1, 123));
        cola.procesarSiguiente();
        return p.getId();
    }
    private AppUser usuario(String email) {
        var u = new AppUser();
        u.setEmail(email);
        return usuarios.saveAndFlush(u);
    }
    private void conectar(AppUser usuario, Long actor) {
        vinculacion.vincular(codigo(vinculacion.crearEnlace(usuario.getId()).url()), actor, actor, "persona");
    }
    private String codigo(String url) { return url.substring(url.lastIndexOf('=') + 1); }
    private void recibir(JsonNode update) { cola.recibir(properties.getWebhookSecret(), update); }
    private JsonNode mensaje(long id, long actor) {
        var update = mapper.createObjectNode().put("update_id", id);
        var mensaje = update.putObject("message").put("message_id", id).put("text", "¿Cuánto llevo gastado?");
        mensaje.putObject("from").put("id", actor).put("is_bot", false);
        mensaje.putObject("chat").put("id", actor).put("type", "private");
        return update;
    }
    private JsonNode callback(long id, long actor, String data) {
        var update = mapper.createObjectNode().put("update_id", id);
        var callback = update.putObject("callback_query").put("id", "callback-" + id).put("data", data);
        callback.putObject("from").put("id", actor).put("is_bot", false);
        callback.putObject("message").put("message_id", 10).putObject("chat").put("id", actor).put("type", "private");
        return update;
    }
}
