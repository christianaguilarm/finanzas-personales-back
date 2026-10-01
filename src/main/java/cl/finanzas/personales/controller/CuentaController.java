package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.CuentaRequest;
import cl.finanzas.personales.dto.CuentaResponse;
import cl.finanzas.personales.service.CuentaService;
import cl.finanzas.personales.service.UsuarioActualService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cuentas")
@RequiredArgsConstructor
public class CuentaController {
    private final CuentaService cuentaService;
    private final UsuarioActualService usuarioActualService;

    @PostMapping
    public ResponseEntity<CuentaResponse> crearCuenta(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CuentaRequest request
    ) {
        return ResponseEntity.ok(cuentaService.crearCuenta(usuarioActualService.idActual(jwt), request));
    }

    /**
     * El usuario sale del token. Una lista vacia es un 200 con [], no un 204 sin body: el front
     * deserializa la respuesta y un null lo rompia.
     */
    @GetMapping
    public ResponseEntity<List<CuentaResponse>> obtenerCuentasPorUsuario(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cuentaService.obtenerCuentasPorUsuario(usuarioActualService.idActual(jwt)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CuentaResponse> actualizarCuenta(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody CuentaRequest request
    ) {
        return ResponseEntity.ok(cuentaService.actualizarCuenta(id, usuarioActualService.idActual(jwt), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCuenta(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        cuentaService.eliminarCuenta(id, usuarioActualService.idActual(jwt));
        return ResponseEntity.noContent().build();
    }
}