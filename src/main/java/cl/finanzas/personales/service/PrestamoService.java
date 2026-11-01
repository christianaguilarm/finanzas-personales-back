package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.PrestamoResponse;
import cl.finanzas.personales.model.TipoTransaccion;
import cl.finanzas.personales.model.Transaccion;
import cl.finanzas.personales.repository.CategoriaRepository;
import cl.finanzas.personales.repository.TransaccionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrestamoService {
    private final CategoriaRepository categoriaRepository;
    private final TransaccionRepository transaccionRepository;

    @Transactional(readOnly = true)
    public List<PrestamoResponse> obtenerPrestamos(Long userId) {
        boolean visible = categoriaRepository.findByUserId(userId).stream()
                .anyMatch(c -> c.isActivo() && "PRESTAMO_EGRESO".equals(c.getCodigoSistema()));
        if (!visible) return List.of();
        return transaccionRepository.findByUser_IdAndCategoria_CodigoSistemaAndTipo(
                        userId, "PRESTAMO_EGRESO", TipoTransaccion.EGRESO).stream()
                .map(this::toResponse)
                .toList();
    }

    private PrestamoResponse toResponse(Transaccion origen) {
        BigDecimal devuelto = transaccionRepository.findByPrestamoOrigen_Id(origen.getId()).stream()
                .map(Transaccion::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PrestamoResponse(origen.getId(), origen.getPrestamoPersona() == null ? "Sin indicar" : origen.getPrestamoPersona(), origen.getFecha(),
                origen.getPrestamoFechaDevolucion(), origen.getMonto(), devuelto,
                origen.getMonto().subtract(devuelto));
    }
}
