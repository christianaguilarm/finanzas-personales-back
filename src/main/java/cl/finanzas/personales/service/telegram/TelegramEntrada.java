package cl.finanzas.personales.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;

/** Solo conserva los campos necesarios; ignora grupos, bots y actualizaciones no soportadas. */
public record TelegramEntrada(Long updateId, Long actorId, Long chatId, String username,
                              String texto, String callbackId, String callbackData, Long messageId) {
    public static TelegramEntrada desde(JsonNode update) {
        var callback = update.path("callback_query");
        boolean esCallback = callback.isObject();
        var message = esCallback ? callback.path("message") : update.path("message");
        var from = esCallback ? callback.path("from") : message.path("from");
        var chat = message.path("chat");
        if (!entero(update.path("update_id")) || update.path("update_id").asLong() < 0
                || !entero(from.path("id")) || !entero(chat.path("id"))
                || from.path("id").asLong() <= 0 || from.path("is_bot").asBoolean()
                || !"private".equals(chat.path("type").asText())
                || from.path("id").asLong() != chat.path("id").asLong()) return null;
        if (esCallback && (!callback.path("id").isTextual() || !callback.path("data").isTextual()
                || !entero(message.path("message_id")))) return null;
        return new TelegramEntrada(update.path("update_id").asLong(), from.path("id").asLong(),
                chat.path("id").asLong(), from.path("username").isTextual() ? from.path("username").asText() : null,
                message.path("text").asText(""), esCallback ? callback.path("id").asText() : null,
                esCallback ? callback.path("data").asText() : null,
                esCallback ? message.path("message_id").asLong() : null);
    }

    private static boolean entero(JsonNode value) { return value.isIntegralNumber() && value.canConvertToLong(); }
}
