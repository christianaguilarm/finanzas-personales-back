package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.AsistenteChatRequest;
import cl.finanzas.personales.dto.AsistenteChatResponse;
import cl.finanzas.personales.service.AsistentePropuestaService;
import cl.finanzas.personales.service.AsistenteService;
import cl.finanzas.personales.service.UsuarioActualService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/asistente")
@RequiredArgsConstructor
public class AsistenteController {
    private final AsistenteService asistente;
    private final AsistentePropuestaService propuestas;
    private final UsuarioActualService usuarioActual;

    @GetMapping("/estado")
    public AsistenteChatResponse.Estado estado() {
        return asistente.estado();
    }

    @PostMapping("/chat")
    public AsistenteChatResponse chat(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody AsistenteChatRequest request) {
        return asistente.conversar(usuarioActual.idActual(jwt), request);
    }

    @PostMapping("/propuestas/{id}/confirmar")
    public AsistenteChatResponse.Confirmacion confirmar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return propuestas.confirmar(usuarioActual.idActual(jwt), id);
    }
}
