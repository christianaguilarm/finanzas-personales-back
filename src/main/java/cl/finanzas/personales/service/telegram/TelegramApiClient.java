package cl.finanzas.personales.service.telegram;

import cl.finanzas.personales.configuration.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.time.Duration;
import java.util.Set;

@Component
public class TelegramApiClient {
    private final TelegramProperties properties;
    private final RestClient client;
    private static final Set<String> METODOS = Set.of("sendMessage", "answerCallbackQuery", "editMessageReplyMarkup");

    public TelegramApiClient(RestClient.Builder builder, TelegramProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        client = builder.requestFactory(factory).build();
    }

    public void enviar(String metodo, JsonNode payload) {
        if (!properties.disponible() || !METODOS.contains(metodo)) throw new TelegramApiException();
        try {
            var response = client.post().uri(properties.getApiBaseUrl() + "/bot" + properties.getBotToken() + "/" + metodo)
                    .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(JsonNode.class);
            if (response == null || !response.path("ok").asBoolean()) throw new TelegramApiException();
        } catch (RestClientException e) {
            // Las URLs de Telegram contienen el token: nunca propagar ni loguear la excepción original.
            throw new TelegramApiException();
        }
    }

    public static class TelegramApiException extends RuntimeException {
        public TelegramApiException() { super("No se pudo entregar la respuesta a Telegram"); }
    }
}
