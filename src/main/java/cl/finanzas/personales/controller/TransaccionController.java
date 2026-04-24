package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.TransaccionFiltroQuery;
import cl.finanzas.personales.dto.TransaccionRequest;
import cl.finanzas.personales.dto.TransaccionResponse;
import cl.finanzas.personales.service.TransaccionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transacciones")
@RequiredArgsConstructor
public class TransaccionController {
    private final TransaccionService transaccionService;

    @GetMapping("/porUsuario/{userId}")
    public ResponseEntity<List<TransaccionResponse>> obtenerTransaccionesPorUsuario(
            @PathVariable Long userId,
            @ModelAttribute TransaccionFiltroQuery filtros
    ) {
        List<TransaccionResponse> transacciones = transaccionService.obtenerTransaccionesPorUsuario(userId, filtros);
        return transacciones.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(transacciones);
    }

    @PostMapping("/")
    public ResponseEntity<TransaccionResponse> crearTransaccion(@Valid @RequestBody TransaccionRequest request) {
        TransaccionResponse response = transaccionService.crearTransaccion(request);
        return ResponseEntity.ok(response);
    }
}
