package cl.finanzas.personales.service;

import cl.finanzas.personales.configuration.AsistenteProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class AsistenteIaClient {
    private final RestClient client;
    private final AsistenteProperties properties;

    public AsistenteIaClient(RestClient.Builder builder, AsistenteProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));
        client = builder.requestFactory(factory).build();
    }

    public JsonNode conversar(List<JsonNode> mensajes, JsonNode herramientas) {
        if (!properties.disponible()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El asistente todavía no está configurado");
        }
        try {
            var response = client.post().uri(properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", properties.getModel(), "messages", mensajes,
                            "tools", herramientas, "temperature", 0.2, "max_tokens", 1800))
                    .retrieve().body(JsonNode.class);
            var message = response == null ? null : response.path("choices").path(0).path("message");
            if (message == null || !message.isObject()) throw new IllegalArgumentException();
            return message;
        } catch (RestClientException | IllegalArgumentException e) {
            // No exponer cuerpos del proveedor, cabeceras ni claves en los errores de la API.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "El proveedor de IA no respondió correctamente. Inténtalo nuevamente");
        }
    }
}
