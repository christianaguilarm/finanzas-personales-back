package cl.finanzas.personales.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Cuadre de los gastos registrados de una cuenta en un periodo. No incluye el monto
 * facturado del estado de cuenta, que el usuario compara a mano: acá va lo que el sistema
 * tiene registrado, para poder notar qué transacción falta.
 */
public record CuadreGastosResponse(
        Long cuentaId,
        String cuentaNombre,
        YearMonth periodo,
        BigDecimal total,
        long cantidadTransacciones,
        long cantidadComprasEnCuotas,
        BigDecimal totalComprasEnCuotas,
        boolean incluyeCuotaCero,
        List<CuadreCategoriaResponse> porCategoria,
        List<CuadreCategoriaResponse> porComercio,
        List<TransaccionResponse> transacciones
) {
}
