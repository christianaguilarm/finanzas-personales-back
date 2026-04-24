package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.CategoriaRequest;
import cl.finanzas.personales.dto.CategoriaResponse;
import cl.finanzas.personales.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/categorias")
@RequiredArgsConstructor
public class CategoriaController {
    private final CategoriaService categoriaService;

    @PostMapping
    public ResponseEntity<CategoriaResponse> crearCategoria(@Valid @RequestBody CategoriaRequest request) {
        CategoriaResponse response = categoriaService.crearCategoria(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/porUsuario/{userId}")
    public ResponseEntity<List<CategoriaResponse>> obtenerCategoriasPorUsuario(@PathVariable Long userId) {
        List<CategoriaResponse> categorias = categoriaService.obtenerCategoriasPorUsuario(userId);
        return categorias.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(categorias);
    }
}
