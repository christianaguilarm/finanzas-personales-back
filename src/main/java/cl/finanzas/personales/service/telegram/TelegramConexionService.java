package cl.finanzas.personales.service.telegram;

import cl.finanzas.personales.configuration.AsistenteProperties;
import cl.finanzas.personales.configuration.TelegramProperties;
import cl.finanzas.personales.dto.TelegramResponse;
import cl.finanzas.personales.model.TelegramConexion;
import cl.finanzas.personales.repository.AppUserRepository;
import cl.finanzas.personales.repository.TelegramConexionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TelegramConexionService {
    private final TelegramConexionRepository repository;
    private final AppUserRepository usuarios;
    private final TelegramProperties properties;
    private final AsistenteProperties ia;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public TelegramResponse.Estado estado(Long userId) {
        var c = repository.findById(userId).orElse(null);
        return new TelegramResponse.Estado(properties.disponible(), c != null && c.getTelegramUserId() != null,
                properties.disponible() ? properties.getBotUsername() : null,
                c == null ? null : c.getTelegramUsername(), c == null ? null : c.getVinculadoEn(), ia.disponible());
    }

    @Transactional
    public TelegramResponse.Enlace crearEnlace(Long userId) {
        if (!properties.disponible()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram todavía no está habilitado");
        usuarios.bloquearPorId(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        var c = repository.bloquearPorUsuario(userId).orElseGet(() -> {
            var nueva = new TelegramConexion();
            nueva.setUserId(userId);
            return nueva;
        });
        if (c.getTelegramUserId() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Desconecta la cuenta actual antes de vincular otra");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        c.setCodigoHash(hash(codigo));
        c.setCodigoExpiraEn(clock.instant().plus(Duration.ofMinutes(10)));
        repository.save(c);
        return new TelegramResponse.Enlace("https://t.me/" + properties.getBotUsername() + "?start=" + codigo, c.getCodigoExpiraEn());
    }

    @Transactional
    public void desvincular(Long userId) {
        usuarios.bloquearPorId(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        repository.bloquearPorUsuario(userId).ifPresent(c -> {
            c.setTelegramUserId(null);
            c.setChatId(null);
            c.setTelegramUsername(null);
            c.setVinculoId(null);
            c.setVinculadoEn(null);
            c.setCodigoHash(null);
            c.setCodigoExpiraEn(null);
            c.setHistorial("[]");
            c.setHistorialActualizadoEn(null);
        });
    }

    @Transactional
    public void vincular(String codigo, Long actorId, Long chatId, String username) {
        if (codigo == null || !codigo.matches("[A-Za-z0-9_-]{43}") || !actorId.equals(chatId)) {
            throw codigoInvalido();
        }
        var c = repository.bloquearPorCodigo(hash(codigo)).orElseThrow(this::codigoInvalido);
        if (c.getCodigoExpiraEn() == null || !clock.instant().isBefore(c.getCodigoExpiraEn()) || c.getTelegramUserId() != null) {
            throw codigoInvalido();
        }
        if (repository.existsByTelegramUserId(actorId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este Telegram ya está vinculado. Desconéctalo primero desde Finanzas Personales");
        }
        c.setTelegramUserId(actorId);
        c.setChatId(chatId);
        c.setTelegramUsername(username == null ? null : username.substring(0, Math.min(64, username.length())));
        c.setVinculoId(UUID.randomUUID());
        c.setVinculadoEn(clock.instant());
        c.setCodigoHash(null);
        c.setCodigoExpiraEn(null);
        c.setHistorial("[]");
        c.setHistorialActualizadoEn(null);
        repository.saveAndFlush(c);
    }

    private ResponseStatusException codigoInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "El enlace venció o ya fue utilizado. Genera uno nuevo desde Conectar Telegram en la app");
    }

    private String hash(String codigo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 no disponible"); }
    }
}
