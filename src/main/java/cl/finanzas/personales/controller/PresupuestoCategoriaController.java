package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.PresupuestoCategoriaRequest;
import cl.finanzas.personales.dto.PresupuestoCategoriaResponse;
import cl.finanzas.personales.service.PresupuestoCategoriaService;
import cl.finanzas.personales.service.UsuarioActualService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/presupuestos")
@RequiredArgsConstructor
public class PresupuestoCategoriaController {
    private final PresupuestoCategoriaService servicio;
    private final UsuarioActualService usuarioActual;

    @GetMapping
    public ResponseEntity<List<PresupuestoCategoriaResponse>> listar(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(servicio.listar(usuarioActual.idActual(jwt)));
    }

    @PutMapping("/{categoriaId}")
    public ResponseEntity<PresupuestoCategoriaResponse> guardar(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long categoriaId, @Valid @RequestBody PresupuestoCategoriaRequest request) {
        return ResponseEntity.ok(servicio.guardar(usuarioActual.idActual(jwt), categoriaId, request));
    }

    @DeleteMapping("/{categoriaId}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long categoriaId) {
        servicio.eliminar(usuarioActual.idActual(jwt), categoriaId);
        return ResponseEntity.noContent().build();
    }
}
