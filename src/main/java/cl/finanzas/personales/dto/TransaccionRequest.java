package cl.finanzas.personales.dto;

import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.model.TipoTransaccion;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

public record TransaccionRequest(
        @NotNull Long userId,
        @NotNull Long cuentaId,
        @NotNull TipoTransaccion tipo,
        @NotNull LocalDate fecha,
        YearMonth periodoFacturacion,
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
        BigDecimal monto,
        Long categoriaId,
        Long subcategoriaId,
        @NotNull MedioPago medio,
        Long comercioId,
        @Size(max = 300) String descripcion,
        boolean esRecurrente,
        @Min(value = 1, message = "El total de cuotas debe ser mayor o igual a 1") Integer totalCuotas,
        @Min(value = 1, message = "La cuota actual debe ser mayor o igual a 1") Integer cuotaActual,
        Set<Long> tagIds
) {
}
