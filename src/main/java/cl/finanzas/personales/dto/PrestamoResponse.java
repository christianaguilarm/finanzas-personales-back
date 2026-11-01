package cl.finanzas.personales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PrestamoResponse(Long transaccionId, String persona, LocalDate fechaPrestamo,
                               LocalDate fechaDevolucion, BigDecimal monto, BigDecimal devuelto,
                               BigDecimal pendiente) {}
