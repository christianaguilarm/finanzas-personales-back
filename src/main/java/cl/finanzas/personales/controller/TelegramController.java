package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.TelegramResponse;
import cl.finanzas.personales.dto.TelegramPreferencias;
import cl.finanzas.personales.service.telegram.TelegramPreferenciasService;
import jakarta.validation.Valid;
import cl.finanzas.personales.service.UsuarioActualService;
import cl.finanzas.personales.service.telegram.TelegramColaService;
import cl.finanzas.personales.service.telegram.TelegramConexionService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
public class TelegramController {
    private final TelegramConexionService conexiones;
    private final TelegramColaService cola;
    private final UsuarioActualService usuario;
    private final TelegramPreferenciasService preferencias;

    @GetMapping("/preferencias")
    public ResponseEntity<TelegramPreferencias> preferencias(@AuthenticationPrincipal Jwt jwt) {
        return sinCache(preferencias.obtener(usuario.idActual(jwt)));
    }

    @PutMapping("/preferencias")
    public ResponseEntity<TelegramPreferencias> guardarPreferencias(@AuthenticationPrincipal Jwt jwt,
                                                                  @Valid @RequestBody TelegramPreferencias request) {
        return sinCache(preferencias.guardar(usuario.idActual(jwt), request));
    }

    @GetMapping("/estado")
    public ResponseEntity<TelegramResponse.Estado> estado(@AuthenticationPrincipal Jwt jwt) {
        return sinCache(conexiones.estado(usuario.idActual(jwt)));
    }

    @PostMapping("/vincular")
    public ResponseEntity<TelegramResponse.Enlace> vincular(@AuthenticationPrincipal Jwt jwt) {
        return sinCache(conexiones.crearEnlace(usuario.idActual(jwt)));
    }

    @PostMapping("/desvincular")
    public ResponseEntity<Void> desvincular(@AuthenticationPrincipal Jwt jwt) {
        conexiones.desvincular(usuario.idActual(jwt));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secreto,
                                        @RequestBody JsonNode update) {
        cola.recibir(secreto, update);
        return ResponseEntity.ok().build();
    }

    private <T> ResponseEntity<T> sinCache(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
