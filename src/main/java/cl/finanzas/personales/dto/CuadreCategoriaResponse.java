package cl.finanzas.personales.dto;

import java.math.BigDecimal;

/**
 * Línea de un desglose del cuadre. Sirve tanto para el desglose por categoría
 * (id y nombre de la categoría) como por comercio (id y nombre del comercio).
 * Una transacción sin categoría o sin comercio cae en la línea con id null.
 */
public record CuadreCategoriaResponse(
        Long id,
        String nombre,
        BigDecimal total,
        long cantidad
) {
}
