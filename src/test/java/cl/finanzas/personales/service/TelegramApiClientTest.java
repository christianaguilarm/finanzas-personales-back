package cl.finanzas.personales.service;

import cl.finanzas.personales.configuration.TelegramProperties;
import cl.finanzas.personales.service.telegram.TelegramApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class TelegramApiClientTest {
    private HttpServer server;
    private TelegramApiClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void configurar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        var p = new TelegramProperties();
        p.setBotToken("123456:token-solo-pruebas");
        p.setBotUsername("finanzas_pruebas_bot");
        p.setWebhookSecret("secreto-solo-pruebas");
        p.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        client = new TelegramApiClient(RestClient.builder(), p);
    }
    @AfterEach
    void cerrar() { server.stop(0); }

    @Test
    void enviaTextoSimpleYNoCredencialesDeAuth0NiDeIA() {
        server.createContext("/bot123456:token-solo-pruebas/sendMessage", exchange -> {
            var body = mapper.readTree(exchange.getRequestBody());
            assertEquals(123, body.path("chat_id").asInt());
            assertEquals("Hola", body.path("text").asText());
            assertFalse(body.has("parse_mode"));
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        client.enviar("sendMessage", mapper.createObjectNode().put("chat_id", 123).put("text", "Hola"));
    }

    @Test
    void unErrorDeTelegramNoExponeLaUrlConElTokenNiElCuerpoDelProveedor() {
        server.createContext("/bot123456:token-solo-pruebas/sendMessage", exchange -> {
            byte[] response = "detalle-sensible".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        var error = assertThrows(TelegramApiClient.TelegramApiException.class,
                () -> client.enviar("sendMessage", mapper.createObjectNode().put("chat_id", 123).put("text", "Hola")));
        assertFalse(error.getMessage().contains("token-solo-pruebas"));
        assertFalse(error.getMessage().contains("detalle-sensible"));
        assertNull(error.getCause());
    }
}
