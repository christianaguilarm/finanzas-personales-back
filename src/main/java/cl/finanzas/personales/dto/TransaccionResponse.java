package cl.finanzas.personales.dto;

import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.model.TipoTransaccion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Set;

public record TransaccionResponse(
        Long id,
        TipoTransaccion tipo,
        LocalDate fecha,
        YearMonth periodoFacturacion,
        BigDecimal monto,
        MedioPago medio,
        String descripcion,
        boolean esRecurrente,
        Integer totalCuotas,
        Integer cuotaActual,
        LocalDateTime creadoEn,
        Long cuentaId,
        Long categoriaId,
        Long subcategoriaId,
        Long comercioId,
        Set<Long> tagIds
) {
}
