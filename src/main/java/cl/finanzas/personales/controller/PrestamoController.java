package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.PrestamoResponse;
import cl.finanzas.personales.service.PrestamoService;
import cl.finanzas.personales.service.UsuarioActualService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/prestamos")
@RequiredArgsConstructor
public class PrestamoController {
    private final PrestamoService prestamoService;
    private final UsuarioActualService usuarioActualService;

    @GetMapping
    public List<PrestamoResponse> obtenerPrestamos(@AuthenticationPrincipal Jwt jwt) {
        return prestamoService.obtenerPrestamos(usuarioActualService.idActual(jwt));
    }
}
