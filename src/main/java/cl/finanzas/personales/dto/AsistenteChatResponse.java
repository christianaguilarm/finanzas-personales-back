package cl.finanzas.personales.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AsistenteChatResponse(String respuesta, List<Propuesta> propuestas, NotaResponse notaGuardada) {
    public AsistenteChatResponse(String respuesta, List<Propuesta> propuestas) { this(respuesta, propuestas, null); }
    public record Propuesta(UUID id, Instant expiraEn, TransaccionRequest transaccion,
                            String cuentaNombre, String categoriaNombre) {}
    public record Estado(boolean disponible, String mensaje) {}
    public record Confirmacion(Long transaccionId, boolean yaConfirmada) {}
}
