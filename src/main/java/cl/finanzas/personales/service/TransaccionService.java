package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.TransaccionRequest;
import cl.finanzas.personales.dto.TransaccionResponse;
import cl.finanzas.personales.mapper.TransaccionMapper;
import cl.finanzas.personales.model.Transaccion;
import cl.finanzas.personales.repository.TagRepository;
import cl.finanzas.personales.repository.TransaccionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

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
}
