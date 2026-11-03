package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.NotaRequest;
import cl.finanzas.personales.dto.NotaResponse;
import cl.finanzas.personales.service.NotaService;
import cl.finanzas.personales.service.UsuarioActualService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/notas")
@RequiredArgsConstructor
public class NotaController {
    private final NotaService notas;
    private final UsuarioActualService usuario;

    @GetMapping
    public List<NotaResponse> listar(@AuthenticationPrincipal Jwt jwt) { return notas.listar(usuario.idActual(jwt)); }

    @GetMapping("/proximas")
    public List<NotaResponse> proximas(@AuthenticationPrincipal Jwt jwt) { return notas.proximas(usuario.idActual(jwt)); }

    @PostMapping
    public NotaResponse crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NotaRequest request) {
        return notas.crear(usuario.idActual(jwt), request);
    }

    @PutMapping("/{id}")
    public NotaResponse actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody NotaRequest request) {
        return notas.actualizar(usuario.idActual(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { notas.eliminar(usuario.idActual(jwt), id); }
}
