package cl.finanzas.personales.service;

import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioActualServiceTest {

    private static final String SUB = "auth0|65f1c2a9b0d3e4f5a6b7c8d9e";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private UsuarioActualService usuarioActualService;

    @BeforeEach
    void relojFijo() {
        // El servicio pide la hora al Clock inyectado (nunca a LocalDateTime.now()), asi que el
        // reloj queda clavado y el resultado es determinista.
        lenient().when(clock.instant()).thenReturn(RELOJ.instant());
        lenient().when(clock.getZone()).thenReturn(RELOJ.getZone());
    }

    @Test
    @DisplayName("un sub desconocido crea un AppUser a partir de los claims")
    void unSubDesconocidoProvisionaElUsuario() {
        when(appUserRepository.findByAuth0Sub(SUB)).thenReturn(Optional.empty());
        when(appUserRepository.saveAndFlush(any(AppUser.class))).thenAnswer(invocacion -> {
            AppUser guardado = invocacion.getArgument(0);
            guardado.setId(99L);
            return guardado;
        });

        AppUser usuario = usuarioActualService.resolverOProvisionar(
                SUB,
                "  Christian.Aguilar@Example.COM ",
                "Christian Aguilar"
        );

        assertThat(usuario.getId()).isEqualTo(99L);
        assertThat(usuario.getAuth0Sub()).isEqualTo(SUB);
        assertThat(usuario.getEmail()).isEqualTo("christian.aguilar@example.com"); // trim + minusculas
        assertThat(usuario.getNombre()).isEqualTo("Christian Aguilar"); // el claim 'name' va entero
        assertThat(usuario.getCreadoEn()).isEqualTo(LocalDateTime.of(2026, 10, 5, 12, 0));
    }

    @Test
    @DisplayName("el usuario recien provisionado nace vacio: sin cuentas, sin categorias, sin password")
    void elUsuarioProvisionadoArrancaVacio() {
        when(appUserRepository.findByAuth0Sub(SUB)).thenReturn(Optional.empty());
        when(appUserRepository.saveAndFlush(any(AppUser.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        AppUser usuario = usuarioActualService.resolverOProvisionar(SUB, "nuevo@example.com", "Nuevo");

        assertThat(usuario.getCuentas()).isEmpty();
        assertThat(usuario.getPasswordHash()).isNull(); // la columna es nullable: no hay login local
        assertThat(usuario.getAuth0Sub()).isEqualTo(SUB);
    }

    @Test
    @DisplayName("un sub ya conocido se reutiliza tal cual, sin tocar la base")
    void unSubConocidoSeReutiliza() {
        AppUser existente = AppUser.builder().id(1L).auth0Sub(SUB).email("ya@example.com").build();
        when(appUserRepository.findByAuth0Sub(SUB)).thenReturn(Optional.of(existente));

        AppUser usuario = usuarioActualService.resolverOProvisionar(SUB, "otro@example.com", "Otro");

        assertThat(usuario).isSameAs(existente);
        assertThat(usuario.getEmail()).isEqualTo("ya@example.com"); // el claim no pisa la fila
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("dos llamadas con el mismo sub van a la base las dos veces: no hay cache para que un "
            + "UPDATE manual de auth0_sub surta efecto de inmediato")
    void cadaLlamadaResuelveContraLaBase() {
        AppUser existente = AppUser.builder().id(1L).auth0Sub(SUB).email("ya@example.com").build();
        when(appUserRepository.findByAuth0Sub(SUB)).thenReturn(Optional.of(existente));

        AppUser primero = usuarioActualService.resolverOProvisionar(SUB, "ya@example.com", "Ya");
        AppUser segundo = usuarioActualService.resolverOProvisionar(SUB, "ya@example.com", "Ya");

        assertThat(segundo).isSameAs(primero);
        // El precio es un SELECT por primary key, que idActual() ya paga en 20 endpoints. A cambio,
        // si alguien bindea un 'sub' a mano en la base, la peticion siguiente ya lo ve.
        verify(appUserRepository, times(2)).findByAuth0Sub(SUB);
    }

    @Test
    @DisplayName("un token sin claim 'email' no se puede provisionar")
    void unTokenSinEmailNoSeProvisiona() {
        when(appUserRepository.findByAuth0Sub(SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioActualService.resolverOProvisionar(SUB, null, "Sin email"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(excepcion -> assertThat(((ResponseStatusException) excepcion).getStatusCode().value())
                        .isEqualTo(403));

        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("un token sin claim 'sub' se rechaza antes de tocar la base")
    void unTokenSinSubSeRechaza() {
        assertThatThrownBy(() -> usuarioActualService.resolverOProvisionar(null, "a@b.com", "X"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(excepcion -> assertThat(((ResponseStatusException) excepcion).getStatusCode().value())
                        .isEqualTo(401));

        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("si otro request se adelanta en la carrera, se usa la fila que escribio")
    void unaCarreraEnAuth0SubUsaLaFilaGanadora() {
        AppUser ganadora = AppUser.builder().id(77L).auth0Sub(SUB).email("ganadora@example.com").build();
        when(appUserRepository.findByAuth0Sub(SUB))
                .thenReturn(Optional.empty())          // todavia no existia
                .thenReturn(Optional.of(ganadora));     // el otro request la escribio primero
        when(appUserRepository.saveAndFlush(any(AppUser.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint ux_app_user_auth0_sub"));

        AppUser usuario = usuarioActualService.resolverOProvisionar(SUB, "carrera@example.com", "Carrera");

        assertThat(usuario).isSameAs(ganadora);
        // Una vez para ver que no existia y otra ya ganada por el otro request.
        verify(appUserRepository, org.mockito.Mockito.times(2)).findByAuth0Sub(eq(SUB));
    }

    @Test
    @DisplayName("un conflicto de integridad que no es la carrera se propaga tal cual")
    void unConfqueoDeIntegridadDistintoSePropaga() {
        DataIntegrityViolationException conflicto = new DataIntegrityViolationException("se rompio otra cosa");
        when(appUserRepository.findByAuth0Sub(SUB)).thenReturn(Optional.empty());
        when(appUserRepository.saveAndFlush(any(AppUser.class))).thenThrow(conflicto);

        assertThatThrownBy(() -> usuarioActualService.resolverOProvisionar(SUB, "a@b.com", "X"))
                .isSameAs(conflicto);
    }
}