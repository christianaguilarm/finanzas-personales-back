package cl.finanzas.personales.service;

import cl.finanzas.personales.model.Cuenta;
import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.repository.CuentaRepository;
import cl.finanzas.personales.repository.SaldoPorCuenta;
import cl.finanzas.personales.repository.TransaccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El arrastre entre periodos: saldo inicial mas los movimientos que si consumen dinero propio,
 * con corte en el inicio del periodo. Acumula los movimientos reales, asi que se verifica aca y
 * no en cada test de proyeccion.
 */
@ExtendWith(MockitoExtension.class)
class SaldoServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CUENTA_CC = 35L;
    private static final List<MedioPago> MEDIOS = MedioPago.queConsumenDineroPropio();

    @Mock
    private CuentaRepository cuentaRepository;
    @Mock
    private TransaccionRepository transaccionRepository;

    @InjectMocks
    private SaldoService saldoService;

    @BeforeEach
    void setUp() {
        // El saldo de una cuenta sola no necesita el listado, asi que el stub es lenient.
        lenient().when(cuentaRepository.findByUserIdAndActivoIsTrue(USER_ID))
                .thenReturn(List.of(cuenta(CUENTA_CC, "CC Banco Chile", "100000.00")));
    }

    @Test
    @DisplayName("El primer mes abre con el saldo inicial de las cuentas, sin arrastre")
    void elPrimerMesAbreConElSaldoInicial() {
        when(transaccionRepository.saldosAcumuladosHasta(USER_ID, LocalDate.of(2026, 5, 1), MEDIOS))
                .thenReturn(List.of());

        BigDecimal saldo = saldoService.saldoAlInicioDelPeriodo(USER_ID, YearMonth.of(2026, 5));

        assertThat(saldo).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("El arrastre del mes siguiente suma lo movido en el mes anterior")
    void elArrastreAcumulaLosPeriodosAnteriores() {
        when(transaccionRepository.saldosAcumuladosHasta(USER_ID, LocalDate.of(2026, 5, 1), MEDIOS))
                .thenReturn(List.of(new Saldo(CUENTA_CC, new BigDecimal("300000.00"))));

        // 100.000 de saldo inicial + 1.000.000 de ingresos - 700.000 de egresos del mes anterior.
        BigDecimal mayo = saldoService.saldoAlInicioDelPeriodo(USER_ID, YearMonth.of(2026, 5));
        assertThat(mayo).isEqualByComparingTo("400000.00");

        when(transaccionRepository.saldosAcumuladosHasta(USER_ID, LocalDate.of(2026, 6, 1), MEDIOS))
                .thenReturn(List.of(new Saldo(CUENTA_CC, new BigDecimal("500000.00"))));

        // El mes 6 vuelve a sumar su propio saldo: 400.000 + 1.000.000 - 800.000.
        BigDecimal junio = saldoService.saldoAlInicioDelPeriodo(USER_ID, YearMonth.of(2026, 6));
        assertThat(junio).isEqualByComparingTo("600000.00");
    }

    @Test
    @DisplayName("El acumulado se pide con corte en el primer dia del periodo")
    void elAcumuladoCortaEnElInicioDelPeriodo() {
        when(transaccionRepository.saldosAcumuladosHasta(any(), any(), any())).thenReturn(List.of());

        saldoService.saldoAlInicioDelPeriodo(USER_ID, YearMonth.of(2026, 8));

        verify(transaccionRepository).saldosAcumuladosHasta(USER_ID, LocalDate.of(2026, 8, 1), MEDIOS);
    }

    @Test
    @DisplayName("Las compras con tarjeta de credito no mueven el arrastre, su pago si")
    void elArrastreIgnoraLoQueNoConsumeDineroPropio() {
        when(transaccionRepository.saldosAcumuladosHasta(any(), any(), any())).thenReturn(List.of());

        saldoService.saldoAlInicioDelPeriodo(USER_ID, YearMonth.of(2026, 8));

        ArgumentCaptor<Collection<MedioPago>> medios = ArgumentCaptor.forClass(Collection.class);
        verify(transaccionRepository).saldosAcumuladosHasta(eq(USER_ID), eq(LocalDate.of(2026, 8, 1)), medios.capture());

        // La compra con CREDITO no se descuenta hasta que se paga la tarjeta, y ese pago va por
        // DEBITO o TRANSFERENCIA, que si consume dinero propio.
        assertThat(medios.getValue())
                .containsExactlyInAnyOrder(MedioPago.EFECTIVO, MedioPago.DEBITO, MedioPago.TRANSFERENCIA, MedioPago.OTRO)
                .doesNotContain(MedioPago.CREDITO, MedioPago.PREPAGO);
    }

    @Test
    @DisplayName("Una cuenta sin movimientos queda con su saldo inicial")
    void unaCuentaSinMovimientosQuedaConSuSaldoInicial() {
        when(transaccionRepository.saldoAcumuladoHasta(USER_ID, CUENTA_CC, LocalDate.of(2026, 10, 6), MEDIOS))
                .thenReturn(BigDecimal.ZERO);

        BigDecimal saldo = saldoService.saldoActual(USER_ID, cuenta(CUENTA_CC, "CC Banco Chile", "318177.00"),
                LocalDate.of(2026, 10, 5));

        assertThat(saldo).isEqualByComparingTo("318177.00");
    }

    @Test
    @DisplayName("Las cuentas se saldan todas juntas, con una sola consulta agrupada")
    void saldaVariasCuentasEnUnaConsulta() {
        Cuenta corriente = cuenta(CUENTA_CC, "CC Banco Chile", "318177.00");
        Cuenta tarjeta = cuenta(37L, "TC Fan", "0.00");

        when(transaccionRepository.saldosAcumuladosHasta(USER_ID, LocalDate.of(2026, 10, 6), MEDIOS))
                .thenReturn(List.of(new Saldo(CUENTA_CC, new BigDecimal("94000.00"))));

        Map<Long, BigDecimal> saldos = saldoService.saldosActuales(USER_ID, List.of(corriente, tarjeta),
                LocalDate.of(2026, 10, 5));

        assertThat(saldos).containsOnlyKeys(CUENTA_CC, 37L);
        assertThat(saldos.get(CUENTA_CC)).isEqualByComparingTo("412177.00");
        // La tarjeta solo tiene compras con tarjeta: no se movio con dinero propio.
        assertThat(saldos.get(37L)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("El saldo actual de la cuenta incluye los movimientos de hoy")
    void elSaldoActualIncluyeLosMovimientosDeHoy() {
        when(transaccionRepository.saldoAcumuladoHasta(any(), any(), any(), any())).thenReturn(new BigDecimal("50000.00"));

        BigDecimal saldo = saldoService.saldoActual(USER_ID, cuenta(CUENTA_CC, "CC Banco Chile", "318177.00"),
                LocalDate.of(2026, 10, 5));

        // El corte de la consulta es exclusivo, asi que para meter el dia de hoy se le pasa manana.
        verify(transaccionRepository).saldoAcumuladoHasta(USER_ID, CUENTA_CC, LocalDate.of(2026, 10, 6), MEDIOS);
        assertThat(saldo).isEqualByComparingTo("368177.00");
    }

    private Cuenta cuenta(Long id, String nombre, String saldoInicial) {
        Cuenta cuenta = new Cuenta();
        cuenta.setId(id);
        cuenta.setNombre(nombre);
        cuenta.setTipo("DEBITO");
        cuenta.setSaldoInicial(new BigDecimal(saldoInicial));
        cuenta.setActivo(true);
        return cuenta;
    }

    /** Fila de la proyección agrupada del repositorio. */
    private record Saldo(Long cuentaId, BigDecimal saldo) implements SaldoPorCuenta {
        @Override
        public Long getCuentaId() {
            return cuentaId;
        }

        @Override
        public BigDecimal getSaldo() {
            return saldo;
        }
    }
}
