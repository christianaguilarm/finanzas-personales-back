package cl.finanzas.personales.dto;

import java.math.BigDecimal;

public record ProyeccionCategoriaResponse(
        Long categoriaPadreId,
        String categoriaNombre,
        BigDecimal total,
        long cantidad
) {
}
