package cl.finanzas.personales.dto;

import cl.finanzas.personales.model.TipoTransaccion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Detalle de una transacción considerada dentro de la proyección del periodo.
 * En compras en cuotas, {@code cuota} indica el número de cuota vigente (ej. "3/12").
 */
public record ProyeccionTransaccionResponse(
        String descripcion,
        String cuenta,
        String categoria,
        String cuota,
        LocalDate fecha,
        BigDecimal monto,
        TipoTransaccion tipo
) {
}
