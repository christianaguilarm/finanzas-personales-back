package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.TransaccionRequest;
import cl.finanzas.personales.dto.TransaccionResponse;
import cl.finanzas.personales.model.PropuestaAsistente;
import cl.finanzas.personales.repository.PropuestaAsistenteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Base efímera: verifica el bloqueo y la idempotencia reales sin tocar Postgres de desarrollo. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop", showSql = false)
@Import({AsistentePropuestaService.class, AsistentePropuestaIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AsistentePropuestaIntegrationTest {
    @Autowired AsistentePropuestaService service;
    @Autowired PropuestaAsistenteRepository repository;
    @Autowired ObjectMapper mapper;
    @MockitoBean TransaccionService transacciones;
    @MockitoBean CuentaService cuentas;
    @MockitoBean CategoriaService categorias;

    @TestConfiguration
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC); }
        @Bean Validator validator() { return Validation.buildDefaultValidatorFactory().getValidator(); }
    }

    @AfterEach
    void limpiar() { repository.deleteAll(); }

    @Test
    void prepararGuardaSoloElBorradorConDatosRevisablesYVencimiento() throws Exception {
        var cuenta = new cl.finanzas.personales.dto.CuentaResponse();
        cuenta.setId(1L);
        cuenta.setNombre("Cuenta corriente");
        when(cuentas.obtenerCuentasPorUsuario(7L)).thenReturn(List.of(cuenta));
        var request = mapper.readValue(movimiento(), TransaccionRequest.class);
        var resultado = service.preparar(7L, request);
        assertEquals("Cuenta corriente", resultado.cuentaNombre());
        assertEquals("Sin categoría", resultado.categoriaNombre());
        assertEquals(Instant.parse("2026-11-01T12:30:00Z"), resultado.expiraEn());
        var guardada = repository.findById(resultado.id()).orElseThrow();
        assertEquals(7L, guardada.getUserId());
        assertEquals(request, mapper.readValue(guardada.getContenido(), TransaccionRequest.class));
        assertNull(guardada.getTransaccionId());
        verifyNoInteractions(transacciones);
    }

    @Test
    void dosConfirmacionesConcurrentesCreanUnaSolaTransaccion() throws Exception {
        var propuesta = guardar(7L, Instant.parse("2026-11-01T12:30:00Z"));
        when(transacciones.crearTransaccion(eq(7L), any())).thenReturn(
                mapper.readValue("{\"id\":99}", TransaccionResponse.class));
        var executor = Executors.newFixedThreadPool(2);
        var inicio = new CyclicBarrier(2);
        Callable<Long> confirmar = () -> {
            inicio.await(5, TimeUnit.SECONDS);
            return service.confirmar(7L, propuesta.getId()).transaccionId();
        };
        try {
            var resultados = executor.invokeAll(List.of(confirmar, confirmar), 10, TimeUnit.SECONDS);
            for (var resultado : resultados) assertEquals(99L, resultado.get());
        } finally {
            executor.shutdownNow();
        }
        verify(transacciones, times(1)).crearTransaccion(eq(7L), any());
        assertEquals(99L, repository.findById(propuesta.getId()).orElseThrow().getTransaccionId());
    }

    @Test
    void otroUsuarioNoPuedeConfirmarLaPropuesta() throws Exception {
        var propuesta = guardar(7L, Instant.parse("2026-11-01T12:30:00Z"));
        var error = assertThrows(ResponseStatusException.class, () -> service.confirmar(8L, propuesta.getId()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(transacciones);
    }

    @Test
    void propuestaVencidaNoGuarda() throws Exception {
        var propuesta = guardar(7L, Instant.parse("2026-11-01T12:00:00Z"));
        var error = assertThrows(ResponseStatusException.class, () -> service.confirmar(7L, propuesta.getId()));
        assertEquals(HttpStatus.GONE, error.getStatusCode());
        verifyNoInteractions(transacciones);
    }

    @Test
    void errorAlGuardarNoMarcaPropuestaComoConfirmada() throws Exception {
        var propuesta = guardar(7L, Instant.parse("2026-11-01T12:30:00Z"));
        when(transacciones.crearTransaccion(eq(7L), any())).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta inactiva"));
        assertThrows(ResponseStatusException.class, () -> service.confirmar(7L, propuesta.getId()));
        assertNull(repository.findById(propuesta.getId()).orElseThrow().getTransaccionId());
    }

    @Test
    void prepararRechazaCuentaAjenaYCompraCreditoSinPeriodo() throws Exception {
        when(cuentas.obtenerCuentasPorUsuario(7L)).thenReturn(List.of());
        var request = mapper.readValue(movimiento(), TransaccionRequest.class);
        assertThrows(ResponseStatusException.class, () -> service.preparar(7L, request));
        var credito = mapper.readValue(movimiento().replace("DEBITO", "CREDITO"), TransaccionRequest.class);
        assertThrows(ResponseStatusException.class, () -> service.preparar(7L, credito));
        assertEquals(0, repository.count());
        verifyNoInteractions(transacciones);
    }

    private PropuestaAsistente guardar(Long userId, Instant expiraEn) throws Exception {
        var propuesta = new PropuestaAsistente();
        propuesta.setId(UUID.randomUUID());
        propuesta.setUserId(userId);
        propuesta.setExpiraEn(expiraEn);
        propuesta.setContenido(movimiento());
        return repository.saveAndFlush(propuesta);
    }
    private String movimiento() {
        return """
                {"cuentaId":1,"tipo":"EGRESO","fecha":"2026-11-01","monto":15000,"medio":"DEBITO"}
                """;
    }
}
