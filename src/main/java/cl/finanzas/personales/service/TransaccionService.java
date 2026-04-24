package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.TransaccionFiltroQuery;
import cl.finanzas.personales.dto.TransaccionRequest;
import cl.finanzas.personales.dto.TransaccionResponse;
import cl.finanzas.personales.mapper.TransaccionMapper;
import cl.finanzas.personales.model.Transaccion;
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

@Service
@RequiredArgsConstructor
public class TransaccionService {

    private final TransaccionRepository transaccionRepository;
    private final TagRepository tagRepository;
    private final TransaccionMapper mapper;

    @Transactional
    public TransaccionResponse crearTransaccion(TransaccionRequest request) {
        Transaccion transaccion = mapper.toEntity(request);

        // Cargar tags si existen
        if (request.tagIds() != null && !request.tagIds().isEmpty()) {
            var tags = new HashSet<>(tagRepository.findAllById(request.tagIds()));
            transaccion.setTags(tags);
        }

        Transaccion guardada = transaccionRepository.save(transaccion);
        return mapper.toResponse(guardada);
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
