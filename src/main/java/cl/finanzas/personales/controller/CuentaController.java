package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.CuentaRequest;
import cl.finanzas.personales.dto.CuentaResponse;
import cl.finanzas.personales.service.CuentaService;
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
@RequestMapping("/cuentas")
@RequiredArgsConstructor
public class CuentaController {
    private final CuentaService cuentaService;

    @PostMapping
    public ResponseEntity<CuentaResponse> crearCuenta(@Valid @RequestBody CuentaRequest request) {
        CuentaResponse cuenta = cuentaService.crearCuenta(request);
        return ResponseEntity.ok(cuenta);
    }

    @GetMapping("/porUsuario/{userId}")
    public ResponseEntity<List<CuentaResponse>> obtenerCuentasPorUsuario(@PathVariable Long userId) {
        List<CuentaResponse> categorias = cuentaService.obtenerCuentasPorUsuario(userId);
        return categorias.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(categorias);
    }
}
