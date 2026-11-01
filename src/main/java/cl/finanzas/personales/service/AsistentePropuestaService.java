package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.AsistenteChatResponse;
import cl.finanzas.personales.dto.TransaccionRequest;
import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.model.PropuestaAsistente;
import cl.finanzas.personales.repository.PropuestaAsistenteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AsistentePropuestaService {
    private final PropuestaAsistenteRepository repository;
    private final TransaccionService transacciones;
    private final CuentaService cuentas;
    private final CategoriaService categorias;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Transactional
    public AsistenteChatResponse.Propuesta preparar(Long userId, TransaccionRequest request) {
        var errores = validator.validate(request);
        if (!errores.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errores.stream()
                    .map(e -> e.getPropertyPath() + ": " + e.getMessage()).sorted()
                    .reduce((a, b) -> a + "; " + b).orElse("Movimiento inválido"));
        }
        if (request.tipo() == null || request.monto() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el tipo y el monto del movimiento");
        }
        if (request.monto().scale() > 2 || request.monto().compareTo(new java.math.BigDecimal("999999999999.99")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto admite hasta 12 enteros y 2 decimales");
        }
        // Primera versión: movimientos puntuales. No inferir planes ni recurrencias incompletos.
        if (request.esRecurrente() || request.totalCuotas() != null || request.cuotaActual() != null
                || request.comercioId() != null || (request.tagIds() != null && !request.tagIds().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para cuotas, recurrentes, comercios o etiquetas usa el formulario de transacciones");
        }
        if (request.medio() == MedioPago.CREDITO && request.periodoFacturacion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el período de facturación de la compra a crédito");
        }
        var cuenta = cuentas.obtenerCuentasPorUsuario(userId).stream()
                .filter(c -> c.getId().equals(request.cuentaId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige una cuenta propia activa"));
        String categoriaNombre = "Sin categoría";
        for (Long categoriaId : new Long[]{request.categoriaId(), request.subcategoriaId()}) {
            if (categoriaId == null) continue;
            var categoria = categorias.obtenerCategoria(categoriaId, userId);
            if (!request.tipo().name().equals(categoria.getTipo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría no corresponde al tipo de movimiento");
            }
            categoriaNombre = categoria.getNombre();
        }
        if (request.subcategoriaId() != null && (request.categoriaId() == null
                || !request.categoriaId().equals(categorias.obtenerCategoria(request.subcategoriaId(), userId).getParentId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La subcategoría no pertenece a la categoría indicada");
        }
        var propuesta = new PropuestaAsistente();
        propuesta.setId(UUID.randomUUID());
        propuesta.setUserId(userId);
        propuesta.setContenido(serializar(request));
        propuesta.setExpiraEn(clock.instant().plus(Duration.ofMinutes(30)));
        repository.save(propuesta);
        return new AsistenteChatResponse.Propuesta(propuesta.getId(), propuesta.getExpiraEn(), request,
                cuenta.getNombre(), categoriaNombre);
    }

    /** Bloqueo y creación en la misma transacción: confirmar dos veces devuelve el mismo id. */
    @Transactional
    public AsistenteChatResponse.Confirmacion confirmar(Long userId, UUID id) {
        var propuesta = repository.buscarParaConfirmar(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Propuesta no encontrada"));
        if (propuesta.getTransaccionId() != null) {
            return new AsistenteChatResponse.Confirmacion(propuesta.getTransaccionId(), true);
        }
        if (!clock.instant().isBefore(propuesta.getExpiraEn())) {
            throw new ResponseStatusException(HttpStatus.GONE, "La propuesta venció. Pide al asistente que la prepare de nuevo");
        }
        TransaccionRequest request;
        try {
            request = mapper.readValue(propuesta.getContenido(), TransaccionRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer la propuesta guardada");
        }
        // Revalida las reglas y la pertenencia de las referencias al momento de guardar.
        var transaccion = transacciones.crearTransaccion(userId, request);
        propuesta.setTransaccionId(transaccion.id());
        repository.save(propuesta);
        return new AsistenteChatResponse.Confirmacion(transaccion.id(), false);
    }

    private String serializar(TransaccionRequest request) {
        try {
            return mapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo preparar el movimiento");
        }
    }
}
