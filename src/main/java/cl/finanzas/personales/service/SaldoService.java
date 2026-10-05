package cl.finanzas.personales.service;

import cl.finanzas.personales.model.Cuenta;
import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.repository.CuentaRepository;
import cl.finanzas.personales.repository.SaldoPorCuenta;
import cl.finanzas.personales.repository.TransaccionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Saldo derivado de una cuenta: {@code saldoInicial + movimientos con consumeDineroPropio()}.
 *
 * El saldo actual nunca se persiste. En la tabla "cuenta" queda solo el ancla con la que el
 * usuario arrancó la cuenta, y todo lo de después se calcula, así que corregir o borrar una
 * transacción no puede dejar un saldo guardado desactualizado.
 */
@Service
@RequiredArgsConstructor
public class SaldoService {

    /**
     * Medios que mueven el saldo derivado. No puede quedar vacío: una lista vacía en el
     * {@code IN} de la consulta la deja sin sintaxis válida.
     */
    private static final List<MedioPago> MEDIOS_QUE_CONSUMEN_DINERO_PROPIO = MedioPago.queConsumenDineroPropio();

    private final CuentaRepository cuentaRepository;
    private final TransaccionRepository transaccionRepository;

    /**
     * Con qué saldo abren todas las cuentas del usuario el periodo indicado.
     *
     * El acumulado se pide agrupado por cuenta y en una sola consulta, en vez de una por cuenta:
     * son cinco cuentas, pero el costo no crece con ellas y el total sale de la misma regla para
     * todas.
     */
    public BigDecimal saldoAlInicioDelPeriodo(Long userId, YearMonth periodo) {
        Map<Long, BigDecimal> acumulados = acumuladosPorCuenta(userId, periodo.atDay(1));

        return cuentaRepository.findByUserIdAndActivoIsTrue(userId).stream()
                .map(cuenta -> saldoInicialMasAcumulado(cuenta, acumulados.get(cuenta.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Saldo actual de cada una de las cuentas, también en una sola consulta agrupada por cuenta. */
    public Map<Long, BigDecimal> saldosActuales(Long userId, List<Cuenta> cuentas, LocalDate hoy) {
        // El corte de la consulta es exclusivo, así que para incluir el día de hoy se le pasa mañana.
        Map<Long, BigDecimal> acumulados = acumuladosPorCuenta(userId, hoy.plusDays(1));

        return cuentas.stream()
                .collect(Collectors.toMap(Cuenta::getId,
                        cuenta -> saldoInicialMasAcumulado(cuenta, acumulados.get(cuenta.getId()))));
    }

    /** Saldo actual de una cuenta sola, movimientos de hoy incluidos. */
    public BigDecimal saldoActual(Long userId, Cuenta cuenta, LocalDate hoy) {
        BigDecimal acumulado = transaccionRepository.saldoAcumuladoHasta(
                userId, cuenta.getId(), hoy.plusDays(1), MEDIOS_QUE_CONSUMEN_DINERO_PROPIO);

        return saldoInicialMasAcumulado(cuenta, acumulado);
    }

    private Map<Long, BigDecimal> acumuladosPorCuenta(Long userId, LocalDate fechaCorte) {
        return transaccionRepository
                .saldosAcumuladosHasta(userId, fechaCorte, MEDIOS_QUE_CONSUMEN_DINERO_PROPIO)
                .stream()
                .collect(Collectors.toMap(SaldoPorCuenta::getCuentaId, SaldoPorCuenta::getSaldo));
    }

    private BigDecimal saldoInicialMasAcumulado(Cuenta cuenta, BigDecimal acumulado) {
        BigDecimal saldoInicial = cuenta.getSaldoInicial() != null ? cuenta.getSaldoInicial() : BigDecimal.ZERO;
        BigDecimal movido = acumulado != null ? acumulado : BigDecimal.ZERO;

        return saldoInicial.add(movido);
    }
}