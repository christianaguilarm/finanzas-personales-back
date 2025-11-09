package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.CategoriaResponse;
import cl.finanzas.personales.repository.CategoriaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    public List<CategoriaResponse> obtenerCategoriasPorUsuario(Long userId) {
        return categoriaRepository.findByUserId(userId)
                .stream()
                .map(cat -> new CategoriaResponse(
                        cat.getId(),
                        cat.getNombre(),
                        cat.getTipo().name(),
                        cat.getIcono(),
                        cat.getColor()
                ))
                .toList();
    }
}
