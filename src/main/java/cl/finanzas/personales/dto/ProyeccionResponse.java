package cl.finanzas.personales.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record ProyeccionResponse(
        YearMonth periodo,
        BigDecimal ingresoTotal,
        BigDecimal egresoTotal,
        BigDecimal total,
        List<ProyeccionCategoriaResponse> ingresosPorCategoria,
        List<ProyeccionCategoriaResponse> egresosPorCategoria,
        List<ProyeccionTransaccionResponse> transacciones
) {
}
