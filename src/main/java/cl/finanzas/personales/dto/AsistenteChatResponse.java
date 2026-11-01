package cl.finanzas.personales.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AsistenteChatResponse(String respuesta, List<Propuesta> propuestas) {
    public record Propuesta(UUID id, Instant expiraEn, TransaccionRequest transaccion,
                            String cuentaNombre, String categoriaNombre) {}
    public record Estado(boolean disponible, String mensaje) {}
    public record Confirmacion(Long transaccionId, boolean yaConfirmada) {}
}
