package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.NotaRequest;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.repository.AppUserRepository;
import cl.finanzas.personales.repository.NotaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import({NotaService.class, NotaServiceTest.Config.class})
class NotaServiceTest {
    @TestConfiguration
    static class Config {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC); }
    }
    @Autowired NotaService notas;
    @Autowired AppUserRepository usuarios;
    @Autowired NotaRepository repository;

    @Test
    void guardaConsultaYCalculaDiasEnChileSinExponerNotasAOtroUsuario() {
        long uno = usuario("uno@example.com");
        long otro = usuario("otro@example.com");
        UUID solicitud = UUID.randomUUID();
        var nota = notas.crear(uno, new NotaRequest("  Pagar luz  ", LocalDate.of(2026, 11, 5), false), solicitud);
        assertEquals("Pagar luz", nota.texto());
        assertEquals(2L, nota.diasRestantes());
        assertEquals(nota.id(), notas.crear(uno, new NotaRequest("Pagar luz", LocalDate.of(2026, 11, 5), false), solicitud).id());
        assertEquals(1, repository.count());
        assertEquals(1, notas.proximas(uno).size());
        assertTrue(notas.proximas(otro).isEmpty());
        assertThrows(ResponseStatusException.class, () -> notas.actualizar(otro, nota.id(), new NotaRequest("Cambiada", null, true)));
        assertThrows(ResponseStatusException.class, () -> notas.eliminar(otro, nota.id()));
        var resuelta = notas.actualizar(uno, nota.id(), new NotaRequest("Pagar luz", LocalDate.of(2026, 11, 5), true));
        assertTrue(resuelta.resuelta());
        assertTrue(notas.proximas(uno).isEmpty());
    }

    @Test
    void notaSinFechaSoloApareceEnListadoYPuedeReabrirse() {
        long usuario = usuario("notas@example.com");
        var nota = notas.crear(usuario, new NotaRequest("Comprar regalo", null, false));
        assertNull(nota.diasRestantes());
        assertTrue(notas.proximas(usuario).isEmpty());
        notas.actualizar(usuario, nota.id(), new NotaRequest("Comprar regalo", null, true));
        assertTrue(notas.listar(usuario).get(0).resuelta());
        notas.actualizar(usuario, nota.id(), new NotaRequest("Comprar regalo", null, false));
        assertFalse(notas.listar(usuario).get(0).resuelta());
    }

    private long usuario(String email) {
        var user = new AppUser();
        user.setEmail(email);
        return usuarios.saveAndFlush(user).getId();
    }
}
