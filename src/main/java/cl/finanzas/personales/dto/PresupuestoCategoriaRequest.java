package cl.finanzas.personales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PresupuestoCategoriaRequest(
        @NotNull @DecimalMin(value = "0.01", message = "El límite debe ser mayor que cero") BigDecimal montoMensual
) {}
