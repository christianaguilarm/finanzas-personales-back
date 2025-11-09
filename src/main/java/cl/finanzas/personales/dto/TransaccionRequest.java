package cl.finanzas.personales.dto;

import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.model.TipoTransaccion;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record TransaccionRequest(
        @NotNull Long userId,
        @NotNull Long cuentaId,
        @NotNull TipoTransaccion tipo,
        @NotNull LocalDate fecha,
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
        BigDecimal monto,
        Long categoriaId,
        Long subcategoriaId,
        @NotNull MedioPago medio,
        Long comercioId,
        @Size(max = 300) String descripcion,
        boolean esRecurrente,
        Set<Long> tagIds
) {
}
