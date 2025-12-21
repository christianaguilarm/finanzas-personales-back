package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.CategoriaResponse;
import cl.finanzas.personales.dto.CuentaResponse;
import cl.finanzas.personales.repository.CategoriaRepository;
import cl.finanzas.personales.repository.CuentaRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CuentaService {
    private final CuentaRepository cuentaRepository;

    public List<CuentaResponse> obtenerCuentasPorUsuario(Long userId) {
        return cuentaRepository.findByUserIdAndActivoIsTrue(userId)
                .stream()
                .map(cat -> new CuentaResponse(
                        cat.getId(),
                        cat.getNombre(),
                        cat.getTipo()
                ))
                .toList();
    }
}
