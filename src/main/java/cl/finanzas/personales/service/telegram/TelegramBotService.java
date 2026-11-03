package cl.finanzas.personales.service.telegram;

import cl.finanzas.personales.dto.AsistenteChatRequest;
import cl.finanzas.personales.dto.AsistenteChatResponse;
import cl.finanzas.personales.model.TelegramConexion;
import cl.finanzas.personales.model.TelegramPropuesta;
import cl.finanzas.personales.repository.TelegramConexionRepository;
import cl.finanzas.personales.repository.TelegramPropuestaRepository;
import cl.finanzas.personales.service.AsistentePropuestaService;
import cl.finanzas.personales.service.AsistenteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** Se ejecuta dentro de la transacción de la bandeja de entrada, nunca en el webhook HTTP. */
@Service
@RequiredArgsConstructor
public class TelegramBotService {
    private final TelegramConexionService vinculacion;
    private final TelegramConexionRepository conexiones;
    private final TelegramPropuestaRepository propuestasTelegram;
    private final AsistentePropuestaService propuestas;
    private final AsistenteService asistente;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public List<TelegramSalida> procesar(TelegramEntrada entrada) {
        String texto = entrada.texto().strip();
        if (entrada.callbackId() == null && texto.startsWith("/start ")) {
            vinculacion.vincular(texto.substring(7).strip(), entrada.actorId(), entrada.chatId(), entrada.username());
            return mensaje(entrada.chatId(), "Telegram vinculado a Finanzas Personales. Puedes consultar tus gastos, guardar notas con fecha o preparar un movimiento. Los movimientos requieren confirmación antes de guardarse; las notas se guardan directamente.\n\nUsa /nuevo para borrar el contexto y /ayuda para ver ejemplos. Puedes desconectar Telegram desde la app.");
        }
        var c = conexiones.findByTelegramUserId(entrada.actorId()).orElse(null);
        if (c == null || !entrada.chatId().equals(c.getChatId())) {
            return respuesta(entrada, "Conecta tu cuenta desde «Conectar Telegram» en Finanzas Personales y abre el enlace de vinculación. No compartas ese enlace.");
        }
        UUID vinculo = c.getVinculoId();
        if (entrada.callbackId() != null) {
            bloquearYValidar(c, entrada, vinculo);
            return callback(entrada, c);
        }
        if (texto.equals("/nuevo")) {
            bloquearYValidar(c, entrada, vinculo);
            c.setHistorial("[]");
            c.setHistorialActualizadoEn(null);
            return mensaje(entrada.chatId(), "Conversación reiniciada. Tus movimientos y propuestas anteriores no se modificaron.");
        }
        if (texto.equals("/start") || texto.equals("/ayuda") || texto.equals("/help")) {
            return mensaje(entrada.chatId(), "Tu cuenta está conectada. Puedes preguntar:\n• ¿Cuánto llevo gastado este mes?\n• Explícame mi proyección de diciembre\n• Recuérdame pagar la luz el 5 de noviembre\n• ¿Qué pagos tengo pendientes?\n• Gasté 15 lucas en bencina con débito en mi cuenta corriente\n\nLas notas se guardan al pedirlo. Para movimientos, revisa la propuesta y pulsa «Confirmar y guardar». /nuevo inicia otra conversación.");
        }
        if (texto.isBlank() || texto.startsWith("/") || texto.length() > 2000) {
            return mensaje(entrada.chatId(), "Por ahora recibo mensajes de texto de hasta 2.000 caracteres. Usa /ayuda para ver ejemplos.");
        }
        var historial = historial(c);
        var solicitudId = UUID.nameUUIDFromBytes(("telegram:" + entrada.updateId()).getBytes(StandardCharsets.UTF_8));
        var resultado = asistente.conversarTelegram(c.getUserId(), new AsistenteChatRequest(texto, List.copyOf(historial), solicitudId));
        // No bloquear el vínculo durante una llamada lenta a la IA. Desconectar debe seguir respondiendo.
        bloquearYValidar(c, entrada, vinculo);
        List<TelegramSalida> salidas = new ArrayList<>(mensaje(entrada.chatId(), resultado.respuesta()));
        for (var p : resultado.propuestas()) {
            var autorizacion = new TelegramPropuesta();
            autorizacion.setId(p.id());
            autorizacion.setUserId(c.getUserId());
            autorizacion.setTelegramUserId(entrada.actorId());
            autorizacion.setVinculoId(c.getVinculoId());
            propuestasTelegram.save(autorizacion);
            var payload = mapper.createObjectNode().put("chat_id", entrada.chatId()).put("text", tarjeta(p));
            payload.set("reply_markup", mapper.valueToTree(Map.of("inline_keyboard", List.of(List.of(
                    Map.of("text", "Confirmar y guardar", "callback_data", "confirmar:" + p.id()),
                    Map.of("text", "Descartar", "callback_data", "descartar:" + p.id()))))));
            salidas.add(new TelegramSalida("sendMessage", payload));
        }
        historial.add(new AsistenteChatRequest.Mensaje("user", texto));
        historial.add(new AsistenteChatRequest.Mensaje("assistant", limitar(resultado.respuesta(), 4000)));
        guardarHistorial(c, historial);
        return salidas;
    }

    private List<TelegramSalida> callback(TelegramEntrada e, TelegramConexion c) {
        String[] partes = e.callbackData().split(":", 2);
        if (partes.length != 2 || !(partes[0].equals("confirmar") || partes[0].equals("descartar"))) {
            return respuesta(e, "Este botón no está disponible. Usa /ayuda para continuar.");
        }
        UUID id;
        try { id = UUID.fromString(partes[1]); }
        catch (IllegalArgumentException ex) { return respuesta(e, "Propuesta no válida."); }
        var permiso = propuestasTelegram.findById(id).orElse(null);
        if (permiso == null || !c.getUserId().equals(permiso.getUserId())
                || !e.actorId().equals(permiso.getTelegramUserId()) || !c.getVinculoId().equals(permiso.getVinculoId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Esta propuesta no pertenece a tu conexión de Telegram");
        }
        if (permiso.isDescartada()) return respuesta(e, "Esta propuesta fue descartada y no puede confirmarse.");
        String respuesta;
        if (partes[0].equals("descartar")) {
            permiso.setDescartada(true);
            respuesta = "Propuesta descartada. Esta acción no elimina movimientos ya guardados.";
        } else {
            var confirmacion = propuestas.confirmar(c.getUserId(), id);
            respuesta = confirmacion.yaConfirmada() ? "Este movimiento ya estaba guardado. No se creó otro."
                    : "Movimiento confirmado y guardado. Puedes verlo en Finanzas Personales.";
            var historial = historial(c);
            historial.add(new AsistenteChatRequest.Mensaje("assistant", "El usuario confirmó la propuesta " + id + ". Transacción guardada: " + confirmacion.transaccionId()));
            guardarHistorial(c, historial);
        }
        var salidas = new ArrayList<>(respuesta(e, respuesta));
        salidas.add(new TelegramSalida("editMessageReplyMarkup", mapper.valueToTree(Map.of(
                "chat_id", e.chatId(), "message_id", e.messageId(), "reply_markup", Map.of("inline_keyboard", List.of())))));
        return salidas;
    }

    public List<TelegramSalida> respuesta(TelegramEntrada entrada, String texto) {
        var salidas = new ArrayList<TelegramSalida>();
        if (entrada.callbackId() != null) {
            salidas.add(new TelegramSalida("answerCallbackQuery", mapper.valueToTree(Map.of(
                    "callback_query_id", entrada.callbackId(), "text", limitar(texto, 150)))));
        }
        salidas.addAll(mensaje(entrada.chatId(), texto));
        return salidas;
    }

    private List<TelegramSalida> mensaje(Long chatId, String texto) {
        var salidas = new ArrayList<TelegramSalida>();
        for (int inicio = 0; inicio < texto.length();) {
            int fin = Math.min(texto.length(), inicio + 4000);
            if (fin < texto.length() && Character.isHighSurrogate(texto.charAt(fin - 1))) fin--;
            salidas.add(new TelegramSalida("sendMessage", mapper.valueToTree(Map.of(
                    "chat_id", chatId, "text", texto.substring(inicio, fin)))));
            inicio = fin;
        }
        return salidas;
    }

    private String tarjeta(AsistenteChatResponse.Propuesta p) {
        var t = p.transaccion();
        var formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL"));
        formato.setMaximumFractionDigits(2);
        return (t.tipo().name().equals("EGRESO") ? "Gasto" : "Ingreso") + " puntual · $" + formato.format(t.monto())
                + "\n" + (t.descripcion() == null ? p.categoriaNombre() : t.descripcion())
                + "\nCuenta: " + p.cuentaNombre() + "\nCategoría: " + p.categoriaNombre()
                + "\nFecha: " + t.fecha() + "\nMedio: " + t.medio()
                + (t.periodoFacturacion() == null ? "" : "\nFacturación: " + t.periodoFacturacion())
                + "\n\nTodavía no está guardado. La propuesta vence a los 30 minutos.";
    }

    private ArrayList<AsistenteChatRequest.Mensaje> historial(TelegramConexion c) {
        if (c.getHistorialActualizadoEn() == null
                || !clock.instant().isBefore(c.getHistorialActualizadoEn().plus(Duration.ofMinutes(30)))) return new ArrayList<>();
        try { return mapper.readValue(c.getHistorial(), new TypeReference<>() {}); }
        catch (JsonProcessingException ex) { return new ArrayList<>(); }
    }

    private void guardarHistorial(TelegramConexion c, List<AsistenteChatRequest.Mensaje> mensajes) {
        var recientes = new ArrayList<>(mensajes.subList(Math.max(0, mensajes.size() - 10), mensajes.size()));
        try {
            String json = mapper.writeValueAsString(recientes);
            while (json.length() > 48000 && !recientes.isEmpty()) {
                recientes.remove(0);
                json = mapper.writeValueAsString(recientes);
            }
            c.setHistorial(json);
        }
        catch (JsonProcessingException ex) { throw new IllegalStateException("No se pudo guardar el contexto de Telegram"); }
        c.setHistorialActualizadoEn(clock.instant());
    }

    private String limitar(String texto, int longitud) {
        int fin = Math.min(texto.length(), longitud);
        if (fin < texto.length() && fin > 0 && Character.isHighSurrogate(texto.charAt(fin - 1))) fin--;
        return texto.substring(0, fin);
    }

    private void bloquearYValidar(TelegramConexion conexion, TelegramEntrada entrada, UUID vinculo) {
        entityManager.refresh(conexion, LockModeType.PESSIMISTIC_WRITE);
        if (!entrada.actorId().equals(conexion.getTelegramUserId()) || !entrada.chatId().equals(conexion.getChatId())
                || !vinculo.equals(conexion.getVinculoId())) {
            throw new ResponseStatusException(HttpStatus.GONE, "La conexión de Telegram cambió. No se guardó esta solicitud");
        }
    }
}
