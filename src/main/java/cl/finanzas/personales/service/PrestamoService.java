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
import java.util.stream.Stream;

/**
 * Préstamos en las dos direcciones:
 * - OTORGADO: egreso con categoría PRESTAMO_EGRESO sin origen (le prestaste a alguien); sus
 *   devoluciones son ingresos PRESTAMO_INGRESO que apuntan a él.
 * - RECIBIDO: ingreso con categoría PRESTAMO_INGRESO sin origen (alguien te prestó); sus
 *   devoluciones son egresos PRESTAMO_EGRESO que apuntan a él.
 * Una transacción con prestamoOrigen es siempre una devolución, nunca un préstamo nuevo.
 */
@Service
@RequiredArgsConstructor
public class PrestamoService {
    public static final String OTORGADO = "OTORGADO";
    public static final String RECIBIDO = "RECIBIDO";

    private final CategoriaRepository categoriaRepository;
    private final TransaccionRepository transaccionRepository;

    @Transactional(readOnly = true)
    public List<PrestamoResponse> obtenerPrestamos(Long userId) {
        boolean visible = categoriaRepository.findByUserId(userId).stream()
                .anyMatch(c -> c.isActivo() && "PRESTAMO_EGRESO".equals(c.getCodigoSistema()));
        if (!visible) return List.of();
        Stream<PrestamoResponse> otorgados = transaccionRepository.findByUser_IdAndCategoria_CodigoSistemaAndTipo(
                        userId, "PRESTAMO_EGRESO", TipoTransaccion.EGRESO).stream()
                .filter(t -> t.getPrestamoOrigen() == null)
                .map(t -> toResponse(t, OTORGADO));
        Stream<PrestamoResponse> recibidos = transaccionRepository.findByUser_IdAndCategoria_CodigoSistemaAndTipo(
                        userId, "PRESTAMO_INGRESO", TipoTransaccion.INGRESO).stream()
                .filter(t -> t.getPrestamoOrigen() == null)
                .map(t -> toResponse(t, RECIBIDO));
        return Stream.concat(otorgados, recibidos).toList();
    }

    private PrestamoResponse toResponse(Transaccion origen, String tipo) {
        BigDecimal devuelto = transaccionRepository.findByPrestamoOrigen_Id(origen.getId()).stream()
                .map(Transaccion::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PrestamoResponse(origen.getId(), tipo,
                origen.getPrestamoPersona() == null ? "Sin indicar" : origen.getPrestamoPersona(), origen.getFecha(),
                origen.getPrestamoFechaDevolucion(), origen.getMonto(), devuelto,
                origen.getMonto().subtract(devuelto));
    }
}
