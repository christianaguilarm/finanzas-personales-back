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
        String cuentaNombre,
        Long categoriaId,
        String categoriaNombre,
        Boolean categoriaEsHija,
        Long categoriaPadreId,
        String categoriaPadreNombre,
        Long subcategoriaId,
        String subcategoriaNombre,
        Long comercioId,
        String comercioNombre,
        Set<Long> tagIds
) {
}
