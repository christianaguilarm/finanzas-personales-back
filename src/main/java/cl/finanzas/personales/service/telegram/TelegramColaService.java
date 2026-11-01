package cl.finanzas.personales.service.telegram;

import cl.finanzas.personales.configuration.TelegramProperties;
import cl.finanzas.personales.model.TelegramUpdate;
import cl.finanzas.personales.repository.TelegramConexionRepository;
import cl.finanzas.personales.repository.TelegramUpdateRepository;
import cl.finanzas.personales.repository.TelegramRegistro;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class TelegramColaService {
    private final TelegramProperties properties;
    private final TelegramUpdateRepository updates;
    private final TelegramRegistro registro;
    private final TelegramConexionRepository conexiones;
    private final TelegramBotService bot;
    private final TelegramApiClient api;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    public TelegramColaService(TelegramProperties properties, TelegramUpdateRepository updates, TelegramRegistro registro,
                               TelegramConexionRepository conexiones, TelegramBotService bot,
                               TelegramApiClient api, ObjectMapper mapper, Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.updates = updates;
        this.registro = registro;
        this.conexiones = conexiones;
        this.bot = bot;
        this.api = api;
        this.mapper = mapper;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public void recibir(String secreto, JsonNode payload) {
        if (!properties.disponible()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram no está habilitado");
        if (secreto == null || !MessageDigest.isEqual(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8),
                secreto.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook no autorizado");
        }
        if (payload == null || !payload.isObject() || payload.toString().length() > 20000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Actualización de Telegram no válida");
        }
        var entrada = TelegramEntrada.desde(payload);
        if (entrada == null || updates.existsById(entrada.updateId())) return;
        var evento = new TelegramUpdate();
        evento.setId(entrada.updateId());
        evento.setActorId(entrada.actorId());
        conexiones.findByTelegramUserId(entrada.actorId()).ifPresent(c -> evento.setVinculoId(c.getVinculoId()));
        evento.setContenido(serializar(entrada));
        evento.setRecibidoEn(clock.instant());
        evento.setProximoIntento(clock.instant());
        // El INSERT tiene su propia transacción; los duplicados nunca reemplazan un evento anterior.
        try { registro.insertar(evento); }
        catch (DataIntegrityViolationException e) {
            if (!updates.existsById(entrada.updateId())) throw new IllegalStateException("No se pudo recibir la actualización de Telegram");
        }
    }

    public boolean procesarSiguiente() {
        Long[] seleccionado = {null};
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                var evento = updates.siguienteParaProcesar().orElse(null);
                if (evento == null) return false;
                seleccionado[0] = evento.getId();
                var entrada = leerEntrada(evento);
                var conexion = conexiones.findByTelegramUserId(entrada.actorId()).orElse(null);
                boolean inicio = entrada.callbackId() == null && entrada.texto().strip().startsWith("/start ");
                List<TelegramSalida> salidas;
                if (!inicio && !Objects.equals(evento.getVinculoId(), conexion == null ? null : conexion.getVinculoId())) {
                    salidas = bot.respuesta(entrada, "Tu conexión cambió. Vuelve a enviar el mensaje después de conectar Telegram desde la app.");
                } else {
                    salidas = bot.procesar(entrada);
                }
                if (inicio) conexiones.findByTelegramUserId(entrada.actorId()).ifPresent(c -> evento.setVinculoId(c.getVinculoId()));
                completar(evento, salidas);
                return true;
            }));
        } catch (RuntimeException error) {
            if (seleccionado[0] == null) throw error;
            // La transacción anterior se revirtió, incluidas propuestas y movimientos. Guardar solo un aviso.
            tx.executeWithoutResult(status -> {
                var evento = updates.bloquearPorId(seleccionado[0]).orElseThrow();
                if (evento.isProcesado()) return;
                String aviso = error instanceof ResponseStatusException rse && rse.getReason() != null
                        ? rse.getReason() : "No pudimos completar la solicitud. Inténtalo nuevamente o revisa tus datos en la app.";
                completar(evento, bot.respuesta(leerEntrada(evento), aviso));
            });
            log.warn("No se pudo procesar la actualización de Telegram {}", seleccionado[0]);
            return true;
        }
    }

    public boolean entregarSiguiente() {
        return Boolean.TRUE.equals(tx.execute(status -> {
            var evento = updates.siguienteParaEntregar(clock.instant()).orElse(null);
            if (evento == null) return false;
            var conexion = conexiones.bloquearPorTelegram(evento.getActorId()).orElse(null);
            if (evento.getVinculoId() != null && (conexion == null || !evento.getVinculoId().equals(conexion.getVinculoId()))) {
                evento.setEntregado(true);
                evento.setRespuestas("[]");
                return true;
            }
            List<TelegramSalida> salidas = leerSalidas(evento);
            if (evento.getEnviados() >= salidas.size()) {
                evento.setEntregado(true);
                evento.setRespuestas("[]");
                return true;
            }
            var salida = salidas.get(evento.getEnviados());
            try {
                api.enviar(salida.metodo(), salida.payload());
            } catch (TelegramApiClient.TelegramApiException e) {
                // Estos dos métodos son auxiliares: un callback vencido no debe bloquear la respuesta visible.
                if (salida.metodo().equals("sendMessage")) {
                    evento.setFallos(evento.getFallos() + 1);
                    evento.setProximoIntento(clock.instant().plusSeconds(30L * evento.getFallos()));
                    log.warn("No se pudo entregar la respuesta de Telegram {} (intento {})", evento.getId(), evento.getFallos());
                    return false;
                }
            }
            evento.setEnviados(evento.getEnviados() + 1);
            if (evento.getEnviados() == salidas.size()) {
                evento.setEntregado(true);
                evento.setRespuestas("[]");
            }
            return true;
        }));
    }

    private void completar(TelegramUpdate evento, List<TelegramSalida> salidas) {
        String contenido = serializar(salidas);
        if (contenido.length() > 64000) throw new IllegalStateException("Respuesta de Telegram demasiado extensa");
        evento.setRespuestas(contenido);
        evento.setContenido("{}"); // No retener texto de mensajes ni códigos de vinculación en la bandeja.
        evento.setProcesado(true);
    }

    private TelegramEntrada leerEntrada(TelegramUpdate evento) {
        try { return mapper.readValue(evento.getContenido(), TelegramEntrada.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("No se pudo leer la actualización de Telegram"); }
    }

    private List<TelegramSalida> leerSalidas(TelegramUpdate evento) {
        try { return mapper.readValue(evento.getRespuestas(), new TypeReference<>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("No se pudo leer la respuesta de Telegram"); }
    }

    private String serializar(Object valor) {
        try { return mapper.writeValueAsString(valor); }
        catch (JsonProcessingException e) { throw new IllegalStateException("No se pudo preparar la actualización de Telegram"); }
    }
}
