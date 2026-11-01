package cl.finanzas.personales.service;

import cl.finanzas.personales.configuration.AsistenteProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AsistenteIaClientTest {
    private HttpServer servidor;
    private final ObjectMapper mapper = new ObjectMapper();
    private AsistenteIaClient client;

    @BeforeEach
    void configurar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.start();
        var properties = new AsistenteProperties();
        properties.setApiKey("clave-solo-para-pruebas");
        properties.setBaseUrl("http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/");
        client = new AsistenteIaClient(RestClient.builder(), properties);
    }

    @AfterEach
    void detener() { servidor.stop(0); }

    @Test
    void enviaModeloHerramientasYAutorizacionSoloAlProveedor() throws Exception {
        servidor.createContext("/v1/chat/completions", exchange -> {
            var body = mapper.readTree(exchange.getRequestBody());
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("Bearer clave-solo-para-pruebas", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("deepseek-flash", body.path("model").asText());
            assertEquals("Hola", body.path("messages").path(0).path("content").asText());
            assertTrue(body.path("tools").isArray());
            byte[] respuesta = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hola\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, respuesta.length);
            exchange.getResponseBody().write(respuesta);
            exchange.close();
        });
        var result = client.conversar(List.of(mapper.createObjectNode().put("role", "user").put("content", "Hola")), mapper.createArrayNode());
        assertEquals("Hola", result.path("content").asText());
    }

    @Test
    void errorDelProveedorNoExponeSuCuerpoNiSecretos() {
        servidor.createContext("/v1/chat/completions", exchange -> {
            byte[] secreto = "detalle sensible del proveedor".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, secreto.length);
            exchange.getResponseBody().write(secreto);
            exchange.close();
        });
        var error = assertThrows(ResponseStatusException.class, () -> client.conversar(List.of(), mapper.createArrayNode()));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertFalse(error.getMessage().contains("sensible"));
        assertNull(error.getCause());
    }
}
