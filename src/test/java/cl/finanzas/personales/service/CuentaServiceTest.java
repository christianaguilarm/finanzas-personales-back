package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.CuentaRequest;
import cl.finanzas.personales.dto.CuentaResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.Cuenta;
import cl.finanzas.personales.repository.CuentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CUENTA_ID = 35L;

    @Mock
    private CuentaRepository cuentaRepository;
    @Mock
    private SaldoService saldoService;

    /** Reloj fijo: el saldo actual se corta con la fecha de hoy, y el test no debe depender del dia. */
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private CuentaService cuentaService;

    private Cuenta cuenta;

    @BeforeEach
    void setUp() {
        cuenta = cuenta("CC Banco Chile", "318177.00");
    }

    @Test
    @DisplayName("Editar una cuenta guarda el saldo inicial que viene en el request")
    void editarGuardaElSaldoInicialDelRequest() {
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(saldoService.saldoActual(eq(USER_ID), any(), any())).thenReturn(new BigDecimal("418177.00"));

        CuentaResponse respuesta = cuentaService.actualizarCuenta(CUENTA_ID, USER_ID,
                new CuentaRequest("CC Banco Chile", "CLP", "DEBITO", new BigDecimal("250000.00")));

        assertThat(cuenta.getSaldoInicial()).isEqualByComparingTo("250000.00");
        assertThat(respuesta.getSaldoInicial()).isEqualByComparingTo("250000.00");
    }

    @Test
    @DisplayName("Editar una cuenta sin saldo inicial no borra el que ya tenia")
    void editarSinSaldoInicialNoLoBorra() {
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(saldoService.saldoActual(eq(USER_ID), any(), any())).thenReturn(new BigDecimal("318177.00"));

        CuentaResponse respuesta = cuentaService.actualizarCuenta(CUENTA_ID, USER_ID,
                new CuentaRequest("CC Banco de Chile", "CLP", "DEBITO", null));

        assertThat(cuenta.getSaldoInicial()).isEqualByComparingTo("318177.00");
        assertThat(respuesta.getSaldoInicial()).isEqualByComparingTo("318177.00");
    }

    @Test
    @DisplayName("El saldo inicial de la cuenta no se recalcula desde sus transacciones")
    void editarNoRecalculaElSaldoInicial() {
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(saldoService.saldoActual(eq(USER_ID), any(), any())).thenReturn(new BigDecimal("-5000.00"));

        CuentaResponse respuesta = cuentaService.actualizarCuenta(CUENTA_ID, USER_ID,
                new CuentaRequest("CC Banco Chile", "CLP", "DEBITO", new BigDecimal("318177.00")));

        // El arrastre va por separado: el ancla no se toca aunque la cuenta haya dado negativo.
        assertThat(respuesta.getSaldoInicial()).isEqualByComparingTo("318177.00");
        assertThat(respuesta.getSaldoActual()).isEqualByComparingTo("-5000.00");
    }

    @Test
    @DisplayName("Listar cuentas devuelve el saldo actual derivado de hoy")
    void listarCuentasDevuelveElSaldoActualDerivado() {
        when(cuentaRepository.findByUserIdAndActivoIsTrue(USER_ID)).thenReturn(List.of(cuenta));
        when(saldoService.saldosActuales(USER_ID, List.of(cuenta), LocalDate.of(2026, 10, 5)))
                .thenReturn(Map.of(CUENTA_ID, new BigDecimal("412177.00")));

        List<CuentaResponse> cuentas = cuentaService.obtenerCuentasPorUsuario(USER_ID);

        assertThat(cuentas).hasSize(1);
        assertThat(cuentas.get(0).getSaldoInicial()).isEqualByComparingTo("318177.00");
        assertThat(cuentas.get(0).getSaldoActual()).isEqualByComparingTo("412177.00");
    }

    @Test
    @DisplayName("Dar de baja una cuenta no toca su saldo inicial")
    void darDeBajaNoTocaElSaldoInicial() {
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        cuentaService.eliminarCuenta(CUENTA_ID, USER_ID);

        assertThat(cuenta.isActivo()).isFalse();
        assertThat(cuenta.getSaldoInicial()).isEqualByComparingTo("318177.00");
        verify(saldoService, never()).saldoActual(any(), any(), any());
    }

    private Cuenta cuenta(String nombre, String saldoInicial) {
        AppUser user = new AppUser();
        user.setId(USER_ID);

        Cuenta cuenta = new Cuenta();
        cuenta.setId(CUENTA_ID);
        cuenta.setNombre(nombre);
        cuenta.setTipo("DEBITO");
        cuenta.setMoneda("CLP");
        cuenta.setSaldoInicial(new BigDecimal(saldoInicial));
        cuenta.setActivo(true);
        cuenta.setUser(user);
        return cuenta;
    }
}
