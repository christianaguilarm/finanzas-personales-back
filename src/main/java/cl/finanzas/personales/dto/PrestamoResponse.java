package cl.finanzas.personales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code tipo}: OTORGADO (te deben) o RECIBIDO (debes). */
public record PrestamoResponse(Long transaccionId, String tipo, String persona, LocalDate fechaPrestamo,
                               LocalDate fechaDevolucion, BigDecimal monto, BigDecimal devuelto,
                               BigDecimal pendiente) {}
