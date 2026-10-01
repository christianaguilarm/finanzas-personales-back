package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.UsuarioResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.service.UsuarioActualService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioActualService usuarioActualService;

    /**
     * Usuario del token. No hay POST /usuarios: el usuario se crea solo al primer ingreso y con
     * su 'sub' de Auth0. Aceptar un alta por API permitiria pre-crear una fila con el email de
     * otra persona.
     */
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> usuarioActual(@AuthenticationPrincipal Jwt jwt) {
        AppUser usuario = usuarioActualService.usuarioActual(jwt);

        return ResponseEntity.ok(new UsuarioResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getCreadoEn()
        ));
    }
}