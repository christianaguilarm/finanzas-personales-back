package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.CuentaResponse;
import cl.finanzas.personales.service.CuentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cuentas")
@RequiredArgsConstructor
public class CuentaController {
    private final CuentaService cuentaService;

    @GetMapping("/porUsuario/{userId}")
    public ResponseEntity<List<CuentaResponse>> obtenerCuentasPorUsuario(@PathVariable Long userId) {
        List<CuentaResponse> categorias = cuentaService.obtenerCuentasPorUsuario(userId);
        return categorias.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(categorias);
    }
}
