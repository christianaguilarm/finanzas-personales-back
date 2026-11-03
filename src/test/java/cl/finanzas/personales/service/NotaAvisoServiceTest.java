package cl.finanzas.personales.service;

import cl.finanzas.personales.configuration.TelegramProperties;
import cl.finanzas.personales.dto.NotaRequest;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.TelegramConexion;
import cl.finanzas.personales.repository.AppUserRepository;
import cl.finanzas.personales.repository.NotaAvisoRepository;
import cl.finanzas.personales.repository.NotaRepository;
import cl.finanzas.personales.repository.TelegramConexionRepository;
import cl.finanzas.personales.service.telegram.NotaAvisoService;
import cl.finanzas.personales.service.telegram.TelegramApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "app.telegram.bot-token=123456:prueba", "app.telegram.bot-username=prueba_bot",
        "app.telegram.webhook-secret=secreto-prueba-largo"
})
@Import({NotaService.class, NotaAvisoService.class, NotaAvisoServiceTest.Config.class})
class NotaAvisoServiceTest {
    static class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-11-03T11:59:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(ahora, zone); }
        public Instant instant() { return ahora; }
    }
    @TestConfiguration
    static class Config {
        @Bean Reloj clock() { return new Reloj(); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean TelegramProperties telegramProperties() {
            var p = new TelegramProperties();
            p.setBotToken("123456:prueba"); p.setBotUsername("prueba_bot"); p.setWebhookSecret("secreto-prueba-largo");
            return p;
        }
    }
    @Autowired Reloj clock;
    @Autowired NotaService notas;
    @Autowired NotaAvisoService avisos;
    @Autowired NotaAvisoRepository pendientes;
    @Autowired NotaRepository notaRepository;
    @Autowired TelegramProperties telegram;
    @Autowired AppUserRepository usuarios;
    @Autowired TelegramConexionRepository conexiones;
    @MockitoBean TelegramApiClient api;

    @Test
    void esperaLasNueveYEncolaUnSoloAvisoPorDia() {
        var nota = preparar();
        avisos.encolarDelDia();
        assertEquals(0, pendientes.count());
        clock.ahora = Instant.parse("2026-11-03T12:00:00Z");
        assertTrue(telegram.disponible());
        assertEquals(1, notaRepository.findByResueltaFalseAndFechaLimiteLessThanEqual(LocalDate.of(2026, 11, 5)).size());
        avisos.encolarDelDia();
        avisos.encolarDelDia();
        assertEquals(1, pendientes.count());
        assertTrue(avisos.entregarSiguiente());
        verify(api, times(1)).enviar(eq("sendMessage"), argThat(payload -> payload.path("text").asText().contains("Te quedan 2 días")));
        assertFalse(avisos.entregarSiguiente());
    }

    @Test
    void cancelaAvisoPendienteAlResolver() {
        var nota = preparar();
        clock.ahora = Instant.parse("2026-11-03T12:00:00Z");
        avisos.encolarDelDia();
        notas.actualizar(nota.userId(), nota.notaId(), new NotaRequest("Pagar luz", LocalDate.of(2026, 11, 5), true));
        assertTrue(avisos.entregarSiguiente());
        verifyNoInteractions(api);
    }

    @Test
    void descartaAvisoAlDesvincularTelegram() {
        var nota = preparar();
        clock.ahora = Instant.parse("2026-11-03T12:00:00Z");
        avisos.encolarDelDia();
        var conexion = conexiones.findById(nota.userId()).orElseThrow();
        conexion.setVinculoId(null);
        conexion.setChatId(null);
        conexiones.saveAndFlush(conexion);
        assertTrue(avisos.entregarSiguiente());
        verifyNoInteractions(api);
    }

    @Test
    void reintentaFalloDeTelegramSinEncolarOtraVez() {
        preparar();
        clock.ahora = Instant.parse("2026-11-03T12:00:00Z");
        avisos.encolarDelDia();
        doThrow(new TelegramApiClient.TelegramApiException()).doNothing().when(api).enviar(eq("sendMessage"), any());
        assertTrue(avisos.entregarSiguiente());
        assertFalse(avisos.entregarSiguiente());
        clock.ahora = clock.ahora.plusSeconds(30);
        assertTrue(avisos.entregarSiguiente());
        assertEquals(1, pendientes.count());
        verify(api, times(2)).enviar(eq("sendMessage"), any());
    }

    private Datos preparar() {
        var user = new AppUser(); user.setEmail(UUID.randomUUID() + "@example.com");
        long userId = usuarios.saveAndFlush(user).getId();
        var conexion = new TelegramConexion(); conexion.setUserId(userId);
        conexion.setTelegramUserId(123L); conexion.setChatId(123L); conexion.setVinculoId(UUID.randomUUID());
        conexiones.saveAndFlush(conexion);
        long notaId = notas.crear(userId, new NotaRequest("Pagar luz", LocalDate.of(2026, 11, 5), false)).id();
        return new Datos(userId, notaId);
    }
    private record Datos(long userId, long notaId) {}
}
