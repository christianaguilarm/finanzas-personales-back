package cl.finanzas.personales.service;

import cl.finanzas.personales.configuration.AsistenteProperties;
import cl.finanzas.personales.dto.AsistenteChatRequest;
import cl.finanzas.personales.dto.AsistenteChatResponse;
import cl.finanzas.personales.repository.TransaccionRepository;
import cl.finanzas.personales.service.telegram.TelegramPreferenciasService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsistenteServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AsistenteProperties properties = new AsistenteProperties();
    private final AsistenteIaClient client = mock(AsistenteIaClient.class);
    private final AsistentePropuestaService propuestas = mock(AsistentePropuestaService.class);
    private final CuentaService cuentas = mock(CuentaService.class);
    private final CategoriaService categorias = mock(CategoriaService.class);
    private final TransaccionService transacciones = mock(TransaccionService.class);
    private final TransaccionRepository repository = mock(TransaccionRepository.class);
    private final TelegramPreferenciasService preferencias = mock(TelegramPreferenciasService.class);
    private AsistenteService service;

    @BeforeEach
    void configurar() {
        properties.setApiKey("clave-de-prueba");
        var clock = Clock.fixed(Instant.parse("2026-11-01T01:00:00Z"), ZoneId.of("America/Santiago"));
        service = new AsistenteService(properties, client, propuestas, cuentas, categorias,
                transacciones, repository, mapper, clock, preferencias);
        when(cuentas.obtenerCuentasPorUsuario(7L)).thenReturn(List.of());
        when(categorias.obtenerCategoriasPorUsuario(7L)).thenReturn(List.of());
    }

    @Test
    void telegramCompletaElBorradorAntesDeValidarloSinPedirCuentaNiPeriodo() throws Exception {
        var conexiones = mock(cl.finanzas.personales.repository.TelegramConexionRepository.class);
        var usuarios = mock(cl.finanzas.personales.repository.AppUserRepository.class);
        var cuentasRepo = mock(cl.finanzas.personales.repository.CuentaRepository.class);
        var categoriasRepo = mock(cl.finanzas.personales.repository.CategoriaRepository.class);
        var usuario = new cl.finanzas.personales.model.AppUser(); usuario.setId(7L);
        var conexion = new cl.finanzas.personales.model.TelegramConexion();
        conexion.setUserId(7L); conexion.setCuentaPredeterminadaId(2L); conexion.setCategoriaPredeterminadaId(10L);
        when(conexiones.findById(7L)).thenReturn(java.util.Optional.of(conexion));
        var cuenta = new cl.finanzas.personales.model.Cuenta(); cuenta.setId(2L); cuenta.setUser(usuario); cuenta.setActivo(true);
        when(cuentasRepo.findById(2L)).thenReturn(java.util.Optional.of(cuenta));
        var categoria = new cl.finanzas.personales.model.Categoria(); categoria.setId(10L); categoria.setUser(usuario);
        categoria.setActivo(true); categoria.setTipo(cl.finanzas.personales.model.TipoTransaccion.EGRESO);
        when(categoriasRepo.findById(10L)).thenReturn(java.util.Optional.of(categoria));
        var reloj = Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), java.time.ZoneOffset.UTC);
        var reales = new TelegramPreferenciasService(conexiones, usuarios, cuentasRepo, categoriasRepo, reloj);
        service = new AsistenteService(properties, client, propuestas, cuentas, categorias, transacciones, repository, mapper, reloj, reales);
        var p = new AsistenteChatResponse.Propuesta(UUID.randomUUID(), Instant.parse("2026-12-01T01:30:00Z"), null, "Cuenta", "Categoría");
        when(propuestas.preparar(eq(7L), any())).thenReturn(p);
        when(client.conversar(anyList(), any())).thenReturn(tool("preparar_movimiento",
                "{\"tipo\":\"EGRESO\",\"fecha\":\"2026-09-15\",\"monto\":5000,\"medio\":\"CREDITO\"}"), respuesta("Revisa la propuesta"));
        var resultado = service.conversarTelegram(7L, request("Registra un gasto de 5 lucas a crédito"));
        var captor = ArgumentCaptor.forClass(cl.finanzas.personales.dto.TransaccionRequest.class);
        verify(propuestas).preparar(eq(7L), captor.capture());
        assertEquals(2L, captor.getValue().cuentaId());
        assertEquals(10L, captor.getValue().categoriaId());
        assertEquals(java.time.YearMonth.of(2026, 11), captor.getValue().periodoFacturacion());
        assertEquals(1, resultado.propuestas().size());
        var herramientas = ArgumentCaptor.forClass(JsonNode.class);
        verify(client, times(2)).conversar(anyList(), herramientas.capture());
        for (var h : herramientas.getValue()) {
            if ("preparar_movimiento".equals(h.path("function").path("name").asText())) {
                assertFalse(h.path("function").path("parameters").path("required").toString().contains("cuentaId"));
            }
        }
        verifyNoInteractions(transacciones);
    }

    @Test
    void sinClaveNoInvocaProveedorNiServiciosFinancieros() {
        properties.setApiKey("");
        assertFalse(service.estado().disponible());
        var error = assertThrows(ResponseStatusException.class,
                () -> service.conversar(7L, request("Hola")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verifyNoInteractions(client, transacciones, propuestas, repository);
    }

    @Test
    void consultaUsaUsuarioAutenticadoYMesDelRelojInyectado() throws Exception {
        when(client.conversar(anyList(), any())).thenReturn(tool("resumen_financiero", "{}"), respuesta("Resumen real"));
        when(repository.resumenParaAsistente(anyLong(), any(), any())).thenReturn(List.of());
        assertEquals("Resumen real", service.conversar(7L, request("¿Cuánto gasté?")).respuesta());
        // UTC ya es noviembre; en Santiago todavía es octubre.
        verify(repository).resumenParaAsistente(7L, java.time.LocalDate.of(2026, 10, 1),
                java.time.LocalDate.of(2026, 11, 1));
        verifyNoInteractions(propuestas);
    }

    @Test
    void periodoInvalidoVuelveComoErrorDeHerramientaYNoComo500() throws Exception {
        when(client.conversar(anyList(), any())).thenReturn(
                tool("resumen_financiero", "{\"periodo\":\"ayer\"}"), respuesta("¿De qué mes?"));
        assertEquals("¿De qué mes?", service.conversar(7L, request("Resumen")).respuesta());
        verifyNoInteractions(repository);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<JsonNode>> captor = ArgumentCaptor.forClass(List.class);
        verify(client, times(2)).conversar(captor.capture(), any());
        assertTrue(captor.getValue().get(3).path("content").asText().contains("error"));
    }

    @Test
    void prepararNoGuardaTransaccionesYUnaCaidaPosteriorConservaLaTarjeta() throws Exception {
        var propuesta = new AsistenteChatResponse.Propuesta(UUID.randomUUID(), Instant.now(), null, "Cuenta", "Bencina");
        when(propuestas.preparar(eq(7L), any())).thenReturn(propuesta);
        when(client.conversar(anyList(), any())).thenReturn(tool("preparar_movimiento", movimiento()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));
        var resultado = service.conversar(7L, request("Gasté 15 lucas en bencina con débito"));
        assertEquals(List.of(propuesta), resultado.propuestas());
        assertTrue(resultado.respuesta().contains("Confirmar y guardar"));
        verifyNoInteractions(transacciones);
    }

    @Test
    void noAceptaUserIdEnArgumentosDelModelo() throws Exception {
        when(client.conversar(anyList(), any())).thenReturn(tool("preparar_movimiento",
                movimiento().replace("{", "{\"userId\":999,")), respuesta("Revisemos los datos"));
        service.conversar(7L, request("Registra un gasto"));
        verifyNoInteractions(propuestas, transacciones);
    }

    @Test
    void mismaPropuestaRepetidaPorElModeloSePreparaUnaSolaVez() throws Exception {
        var propuesta = new AsistenteChatResponse.Propuesta(UUID.randomUUID(), Instant.now(), null, "Cuenta", "Bencina");
        when(propuestas.preparar(eq(7L), any())).thenReturn(propuesta);
        when(client.conversar(anyList(), any())).thenReturn(tool("preparar_movimiento", movimiento()),
                tool("preparar_movimiento", movimiento()), respuesta("Listo"));
        var result = service.conversar(7L, request("Prepara el gasto"));
        assertEquals(1, result.propuestas().size());
        verify(propuestas, times(1)).preparar(eq(7L), any());
    }

    @Test
    void acotaRondasYRechazaFuncionesNoPermitidas() throws Exception {
        when(client.conversar(anyList(), any())).thenReturn(tool("ejecutar_sql", "{}"));
        assertTrue(service.conversar(7L, request("Hola")).respuesta().contains("más específica"));
        verify(client, times(4)).conversar(anyList(), any());
        verifyNoInteractions(repository, transacciones, propuestas);
    }

    @Test
    void rechazaOtraConsultaSimultaneaDelMismoUsuarioYLiberaElCupo() throws Exception {
        when(client.conversar(anyList(), any())).thenAnswer(invocation -> {
            var error = assertThrows(ResponseStatusException.class,
                    () -> service.conversar(7L, request("Otra consulta")));
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
            return respuesta("Hola");
        });
        service.conversar(7L, request("Hola"));
        service.conversar(7L, request("Hola otra vez"));
        verify(client, times(2)).conversar(anyList(), any());
    }

    private AsistenteChatRequest request(String texto) {
        return new AsistenteChatRequest(texto, List.of());
    }
    private JsonNode respuesta(String texto) {
        return mapper.createObjectNode().put("role", "assistant").put("content", texto);
    }
    private JsonNode tool(String nombre, String argumentos) throws Exception {
        var msg = mapper.createObjectNode().put("role", "assistant");
        var tool = msg.putArray("tool_calls").addObject().put("id", "call_1").put("type", "function");
        tool.putObject("function").put("name", nombre).put("arguments", argumentos);
        return msg;
    }
    private String movimiento() {
        return """
                {"cuentaId":1,"tipo":"EGRESO","fecha":"2026-10-31","monto":15000,"medio":"DEBITO"}
                """;
    }
}
