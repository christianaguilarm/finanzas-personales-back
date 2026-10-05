package cl.finanzas.personales.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Proyección de un periodo.
 *
 * {@code saldoInicio} es el arrastre: el saldo con el que abre el periodo, derivado de las
 * cuentas como saldo inicial más los movimientos propios de los periodos anteriores. Va aparte
 * de los ingresos porque no es un ingreso del periodo: si se sumara a {@code ingresoTotal}, el
 * total del mes quedaría inflado y no se podría comparar contra un estado de cuenta.
 */
public record ProyeccionResponse(
        YearMonth periodo,
        BigDecimal saldoInicio,
        BigDecimal ingresoTotal,
        BigDecimal egresoTotal,
        BigDecimal total,
        List<ProyeccionCategoriaResponse> ingresosPorCategoria,
        List<ProyeccionCategoriaResponse> egresosPorCategoria,
        List<ProyeccionTransaccionResponse> transacciones
) {
}
