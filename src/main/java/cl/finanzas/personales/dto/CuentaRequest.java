package cl.finanzas.personales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CuentaRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 80) String nombre,
        @NotBlank @Size(min = 3, max = 3) String moneda,
        @NotBlank @Size(max = 40) String tipo,
        @NotNull @DecimalMin(value = "0.00", message = "El saldo inicial no puede ser negativo") BigDecimal saldoInicial
) {
}

