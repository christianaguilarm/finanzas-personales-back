package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.ComercioRequest;
import cl.finanzas.personales.dto.ComercioResponse;
import cl.finanzas.personales.service.ComercioService;
import cl.finanzas.personales.service.UsuarioActualService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/comercios")
@RequiredArgsConstructor
public class ComercioController {
    private final ComercioService comercioService;
    private final UsuarioActualService usuarioActualService;

    @GetMapping
    public ResponseEntity<List<ComercioResponse>> obtenerComercios(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(comercioService.obtenerComercios(usuarioActualService.idActual(jwt)));
    }

    @PostMapping
    public ResponseEntity<ComercioResponse> crearComercio(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ComercioRequest request) {
        return ResponseEntity.status(201).body(comercioService.crearComercio(usuarioActualService.idActual(jwt), request));
    }
}
