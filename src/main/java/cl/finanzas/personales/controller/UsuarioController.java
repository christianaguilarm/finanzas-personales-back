package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.UsuarioRequest;
import cl.finanzas.personales.dto.UsuarioResponse;
import cl.finanzas.personales.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<UsuarioResponse> crearUsuario(@Valid @RequestBody UsuarioRequest request) {
        UsuarioResponse response = usuarioService.crearUsuario(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

