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
import java.util.ArrayList;
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
        BigDecimal devuelto = devueltoAplicado(origen.getMonto(),
                transaccionRepository.findByPrestamoOrigen_Id(origen.getId()));
        return new PrestamoResponse(origen.getId(), tipo,
                origen.getPrestamoPersona() == null ? "Sin indicar" : origen.getPrestamoPersona(), origen.getFecha(),
                origen.getPrestamoFechaDevolucion(), origen.getMonto(), devuelto,
                origen.getMonto().subtract(devuelto));
    }

    /**
     * Cuánto del préstamo quedó devuelto, nunca más que el préstamo.
     * Una devolución en cuotas (totalCuotas > 1) guarda el valor de cada cuota. Si indica
     * prestamoMontoDevuelto, aporta exactamente eso (el resto del plan son intereses). Si no,
     * el préstamo se saldó al pagarlo con la tarjeta: aporta cuota × totalCuotas. Si eso supera el préstamo, el
     * exceso son intereses de la tarjeta y no cuenta. Un saldo menor que una unidad por cuota
     * (166.666 × 3 contra 500.000) es redondeo de la división y se da por pagado.
     * Las filas del mismo plan (cuota 1/12, 2/12... de montos equivalentes) se cuentan una vez.
     */
    public static BigDecimal devueltoAplicado(BigDecimal montoPrestamo, List<Transaccion> devoluciones) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal toleranciaRedondeo = BigDecimal.ZERO;
        List<Transaccion> planes = new ArrayList<>();
        for (Transaccion devolucion : devoluciones) {
            if (devolucion.getMonto() == null) continue;
            if (!esEnCuotas(devolucion)) {
                total = total.add(devolucion.getMonto());
                continue;
            }
            if (planes.stream().anyMatch(plan -> mismoPlan(plan, devolucion))) continue;
            planes.add(devolucion);
            BigDecimal montoDeclarado = montoDevueltoDelPlan(devolucion, devoluciones);
            if (montoDeclarado != null) {
                // El usuario indicó cuánto del préstamo salda el plan; el resto son intereses.
                total = total.add(montoDeclarado);
                continue;
            }
            total = total.add(devolucion.getMonto().multiply(BigDecimal.valueOf(devolucion.getTotalCuotas())));
            toleranciaRedondeo = toleranciaRedondeo.add(BigDecimal.valueOf(devolucion.getTotalCuotas()));
        }
        if (total.compareTo(montoPrestamo) >= 0 || montoPrestamo.subtract(total).compareTo(toleranciaRedondeo) < 0) {
            return montoPrestamo;
        }
        return total;
    }

    /** El monto devuelto declarado en cualquier fila del plan, o null si ninguna lo indica. */
    private static BigDecimal montoDevueltoDelPlan(Transaccion ancla, List<Transaccion> devoluciones) {
        return devoluciones.stream()
                .filter(d -> d.getPrestamoMontoDevuelto() != null && d.getMonto() != null && mismoPlan(ancla, d))
                .map(Transaccion::getPrestamoMontoDevuelto)
                .findFirst().orElse(null);
    }

    public static boolean esEnCuotas(Transaccion transaccion) {
        return transaccion.getTotalCuotas() != null && transaccion.getTotalCuotas() > 1;
    }

    /** Misma cantidad de cuotas y montos equivalentes (absorbe ajustes de tasa entre cuotas). */
    public static boolean mismoPlan(Transaccion primera, Transaccion segunda) {
        return esEnCuotas(primera) && esEnCuotas(segunda)
                && primera.getTotalCuotas().equals(segunda.getTotalCuotas())
                && primera.getMonto().subtract(segunda.getMonto()).abs().compareTo(BigDecimal.TEN) <= 0;
    }
}
