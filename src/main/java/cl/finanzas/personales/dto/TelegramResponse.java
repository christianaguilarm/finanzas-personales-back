package cl.finanzas.personales.dto;

import java.time.Instant;

public final class TelegramResponse {
    private TelegramResponse() {}

    public record Estado(boolean disponible, boolean vinculado, String botUsername,
                         String telegramUsername, Instant vinculadoEn, boolean asistenteDisponible) {}
    public record Enlace(String url, Instant expiraEn) {}
}
