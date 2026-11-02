package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.ComercioRequest;
import cl.finanzas.personales.dto.ComercioResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.Comercio;
import cl.finanzas.personales.repository.ComercioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComercioService {
    private final ComercioRepository comercioRepository;

    @Transactional(readOnly = true)
    public List<ComercioResponse> obtenerComercios(Long userId) {
        return comercioRepository.findByUserIdOrderByNombreAsc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ComercioResponse crearComercio(Long userId, ComercioRequest request) {
        Comercio comercio = new Comercio();
        AppUser user = new AppUser();
        user.setId(userId);
        comercio.setUser(user);
        comercio.setNombre(request.nombre().strip());
        comercio.setRubro(request.rubro() == null || request.rubro().isBlank() ? null : request.rubro().strip());
        return toResponse(comercioRepository.save(comercio));
    }

    private ComercioResponse toResponse(Comercio comercio) {
        return new ComercioResponse(comercio.getId(), comercio.getNombre(), comercio.getRubro());
    }
}
