package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.TelegramPreferencias;
import cl.finanzas.personales.model.*;
import cl.finanzas.personales.repository.*;
import cl.finanzas.personales.service.telegram.TelegramPreferenciasService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramPreferenciasServiceTest {
    private final TelegramConexionRepository conexiones = mock(TelegramConexionRepository.class);
    private final AppUserRepository usuarios = mock(AppUserRepository.class);
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final CategoriaRepository categorias = mock(CategoriaRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    // En UTC es diciembre; en Chile sigue siendo noviembre.
    private final Clock clock = Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), ZoneOffset.UTC);
    private final TelegramPreferenciasService service = new TelegramPreferenciasService(conexiones, usuarios, cuentas, categorias, clock);
    private AppUser usuario;

    @BeforeEach
    void configurar() {
        usuario = new AppUser();
        usuario.setId(7L);
        when(usuarios.bloquearPorId(7L)).thenReturn(Optional.of(usuario));
        var cuenta = new Cuenta();
        cuenta.setId(2L); cuenta.setUser(usuario); cuenta.setActivo(true);
        when(cuentas.findById(2L)).thenReturn(Optional.of(cuenta));
        when(categorias.findById(10L)).thenReturn(Optional.of(categoria(10L, usuario)));
    }

    @Test
    void completaDatosOmitidosConPreferenciasYMesActualDeChile() {
        var argumentos = mapper.createObjectNode().put("tipo", "EGRESO").put("fecha", "2026-09-15");
        var resultado = service.completar(7L, new TelegramPreferencias(2L, 10L), argumentos);
        assertEquals(2L, resultado.path("cuentaId").asLong());
        assertEquals(10L, resultado.path("categoriaId").asLong());
        assertEquals("2026-11", resultado.path("periodoFacturacion").asText());
        assertFalse(resultado.has("medio")); // No inventar valores que no se pidieron como preferencia.
        assertFalse(argumentos.has("cuentaId"));
    }

    @Test
    void unaSubcategoriaPredeterminadaIncluyeSuPadre() {
        var hija = categoria(11L, usuario);
        hija.setParent(categoria(10L, usuario));
        when(categorias.findById(11L)).thenReturn(Optional.of(hija));
        var resultado = service.completar(7L, new TelegramPreferencias(null, 11L), gasto());
        assertEquals(10L, resultado.path("categoriaId").asLong());
        assertEquals(11L, resultado.path("subcategoriaId").asLong());
    }

    @Test
    void valoresExplicitosTienenPrioridadInclusoSiLaPreferenciaYaNoExiste() {
        var argumentos = gasto().put("cuentaId", 8).put("categoriaId", 12).put("periodoFacturacion", "2027-03");
        assertEquals(argumentos, service.completar(7L, new TelegramPreferencias(999L, 998L), argumentos));
        verifyNoInteractions(cuentas, categorias);
    }

    @Test
    void noAplicaCategoriaDeGastoAIngresosNiCompletaUnaCategoriaExplicitaConOtroPadre() {
        var ingreso = mapper.createObjectNode().put("tipo", "INGRESO");
        assertFalse(service.completar(7L, new TelegramPreferencias(null, 10L), ingreso).has("categoriaId"));
        var subcategoriaExplicita = gasto().put("subcategoriaId", 44);
        assertFalse(service.completar(7L, new TelegramPreferencias(null, 10L), subcategoriaExplicita).has("categoriaId"));
        verifyNoInteractions(categorias);
    }

    @Test
    void sinPreferenciasSoloCompletaPeriodo() {
        var resultado = service.completar(7L, new TelegramPreferencias(null, null), gasto());
        assertFalse(resultado.has("cuentaId"));
        assertFalse(resultado.has("categoriaId"));
        assertEquals("2026-11", resultado.path("periodoFacturacion").asText());
    }

    @Test
    void guardaPreferenciasParaElUsuarioAunqueTodaviaNoVinculeTelegram() {
        var preferencias = new TelegramPreferencias(2L, 10L);
        assertEquals(preferencias, service.guardar(7L, preferencias));
        var captor = org.mockito.ArgumentCaptor.forClass(TelegramConexion.class);
        verify(conexiones).save(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(2L, captor.getValue().getCuentaPredeterminadaId());
        assertNull(captor.getValue().getTelegramUserId());
    }

    @Test
    void permiteQuitarPreferenciasSinAlterarElVinculoNiLasPropuestas() {
        var conexion = new TelegramConexion();
        conexion.setUserId(7L); conexion.setTelegramUserId(123L);
        conexion.setCuentaPredeterminadaId(2L); conexion.setCategoriaPredeterminadaId(10L);
        when(conexiones.bloquearPorUsuario(7L)).thenReturn(Optional.of(conexion));
        service.guardar(7L, new TelegramPreferencias(null, null));
        assertNull(conexion.getCuentaPredeterminadaId());
        assertNull(conexion.getCategoriaPredeterminadaId());
        assertEquals(123L, conexion.getTelegramUserId());
    }

    @Test
    void rechazaReferenciasAjenasInactivasIngresosYPrestamos() {
        var ajeno = new AppUser(); ajeno.setId(8L);
        var cuentaAjena = new Cuenta(); cuentaAjena.setId(20L); cuentaAjena.setUser(ajeno); cuentaAjena.setActivo(true);
        when(cuentas.findById(20L)).thenReturn(Optional.of(cuentaAjena));
        assertThrows(ResponseStatusException.class, () -> service.guardar(7L, new TelegramPreferencias(20L, null)));
        cuentaAjena.setUser(usuario); cuentaAjena.setActivo(false);
        assertThrows(ResponseStatusException.class, () -> service.completar(7L, new TelegramPreferencias(20L, null), gasto()));
        var categoria = categoria(30L, ajeno);
        when(categorias.findById(30L)).thenReturn(Optional.of(categoria));
        assertThrows(ResponseStatusException.class, () -> service.guardar(7L, new TelegramPreferencias(null, 30L)));
        categoria.setUser(usuario); categoria.setActivo(false);
        assertThrows(ResponseStatusException.class, () -> service.guardar(7L, new TelegramPreferencias(null, 30L)));
        categoria.setActivo(true); categoria.setTipo(TipoTransaccion.INGRESO);
        assertThrows(ResponseStatusException.class, () -> service.guardar(7L, new TelegramPreferencias(null, 30L)));
        categoria.setTipo(TipoTransaccion.EGRESO); categoria.setCodigoSistema("PRESTAMO_EGRESO");
        assertThrows(ResponseStatusException.class, () -> service.guardar(7L, new TelegramPreferencias(null, 30L)));
        verify(conexiones, never()).save(any());
    }

    private Categoria categoria(Long id, AppUser user) {
        var c = new Categoria(); c.setId(id); c.setUser(user); c.setTipo(TipoTransaccion.EGRESO); c.setActivo(true);
        return c;
    }
    private ObjectNode gasto() { return mapper.createObjectNode().put("tipo", "EGRESO"); }
}
