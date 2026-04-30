package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.TransaccionFiltroQuery;
import cl.finanzas.personales.dto.TransaccionRequest;
import cl.finanzas.personales.dto.TransaccionResponse;
import cl.finanzas.personales.mapper.TransaccionMapper;
import cl.finanzas.personales.model.Cuenta;
import cl.finanzas.personales.model.TipoTransaccion;
import cl.finanzas.personales.model.Transaccion;
import cl.finanzas.personales.repository.CuentaRepository;
import cl.finanzas.personales.repository.TagRepository;
import cl.finanzas.personales.repository.TransaccionRepository;
import cl.finanzas.personales.repository.specification.TransaccionSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TransaccionService {

    private final TransaccionRepository transaccionRepository;
    private final CuentaRepository cuentaRepository;
    private final TagRepository tagRepository;
    private final TransaccionMapper mapper;

    @Transactional
    public TransaccionResponse crearTransaccion(TransaccionRequest request) {
        Cuenta cuenta = cuentaRepository.findByUserIdAndActivoIsTrue(request.userId())
                .stream()
                .filter(c -> c.getId().equals(request.cuentaId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La cuenta no existe, no pertenece al usuario o está inactiva"
                ));

        TipoTransaccion tipoResuelto = resolverTipoTransaccion(request, cuenta);

        if ((request.totalCuotas() == null) != (request.cuotaActual() == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Para compras en cuotas debes enviar totalCuotas y cuotaActual juntos"
            );
        }

        if (request.totalCuotas() != null) {
            if (request.medio() != cl.finanzas.personales.model.MedioPago.CREDITO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las cuotas solo aplican para medio CREDITO");
            }

            if (request.cuotaActual() > request.totalCuotas()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "cuotaActual no puede ser mayor que totalCuotas"
                );
            }
        }

        Transaccion transaccion = mapper.toEntity(request, tipoResuelto);
        transaccion.setCuenta(cuenta);

        // Cargar tags si existen
        if (request.tagIds() != null && !request.tagIds().isEmpty()) {
            var tags = new HashSet<>(tagRepository.findAllById(request.tagIds()));
            transaccion.setTags(tags);
        }

        Transaccion guardada = transaccionRepository.save(transaccion);
        return mapper.toResponse(guardada);
    }

    private TipoTransaccion resolverTipoTransaccion(TransaccionRequest request, Cuenta cuenta) {
        TipoTransaccion tipoDesdeCuenta = parseTipoTransaccionCuenta(cuenta.getTipo());

        if (request.tipo() != null && request.tipo() != tipoDesdeCuenta) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El tipo enviado no coincide con el tipo de la cuenta"
            );
        }

        return tipoDesdeCuenta;
    }

    private TipoTransaccion parseTipoTransaccionCuenta(String tipoCuenta) {
        if (tipoCuenta == null || tipoCuenta.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cuenta no tiene un tipo válido para derivar la transacción"
            );
        }

        try {
            return TipoTransaccion.valueOf(tipoCuenta.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El tipo de la cuenta no coincide con un TipoTransaccion válido"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<TransaccionResponse> obtenerTransaccionesPorUsuario(Long userId, TransaccionFiltroQuery filtros) {
        if (filtros.getFecha() != null && (filtros.getFechaDesde() != null || filtros.getFechaHasta() != null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No puedes usar fecha exacta junto con fechaDesde o fechaHasta"
            );
        }

        if (filtros.getFechaDesde() != null && filtros.getFechaHasta() != null
                && filtros.getFechaDesde().isAfter(filtros.getFechaHasta())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fechaDesde no puede ser mayor que fechaHasta");
        }

        if (filtros.getMontoMin() != null && filtros.getMontoMax() != null
                && filtros.getMontoMin().compareTo(filtros.getMontoMax()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "montoMin no puede ser mayor que montoMax");
        }

        return transaccionRepository.findAll(TransaccionSpecifications.conFiltros(userId, filtros))
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}
