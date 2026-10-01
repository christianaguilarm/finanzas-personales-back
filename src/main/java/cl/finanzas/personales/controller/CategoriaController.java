package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.CategoriaRequest;
import cl.finanzas.personales.dto.CategoriaResponse;
import cl.finanzas.personales.service.CategoriaService;
import cl.finanzas.personales.service.UsuarioActualService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/categorias")
@RequiredArgsConstructor
public class CategoriaController {
    private final CategoriaService categoriaService;
    private final UsuarioActualService usuarioActualService;

    @PostMapping
    public ResponseEntity<CategoriaResponse> crearCategoria(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CategoriaRequest request
    ) {
        return ResponseEntity.ok(categoriaService.crearCategoria(usuarioActualService.idActual(jwt), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponse> actualizarCategoria(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody CategoriaRequest request
    ) {
        return ResponseEntity.ok(categoriaService.actualizarCategoria(id, usuarioActualService.idActual(jwt), request));
    }

    /**
     * El usuario sale del token. Una lista vacia es un 200 con [], no un 204 sin body: el front
     * deserializa la respuesta y un null lo rompia.
     */
    @GetMapping
    public ResponseEntity<List<CategoriaResponse>> obtenerCategoriasPorUsuario(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(categoriaService.obtenerCategoriasPorUsuario(usuarioActualService.idActual(jwt)));
    }

    @PostMapping("/{id}/eliminar")
    public ResponseEntity<Void> eliminarCategoria(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        categoriaService.eliminarCategoria(id, usuarioActualService.idActual(jwt));
        return ResponseEntity.noContent().build();
    }
}