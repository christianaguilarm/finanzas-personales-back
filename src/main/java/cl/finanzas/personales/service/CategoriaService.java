package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.CategoriaRequest;
import cl.finanzas.personales.dto.CategoriaResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.Categoria;
import cl.finanzas.personales.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;

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

    @Transactional
    public CategoriaResponse crearCategoria(CategoriaRequest request) {
        Categoria categoria = new Categoria();
        categoria.setNombre(request.nombre());
        categoria.setTipo(request.tipo());
        categoria.setIcono(request.icono());
        categoria.setColor(request.color());

        AppUser user = new AppUser();
        user.setId(request.userId());
        categoria.setUser(user);

        if (request.parentId() != null) {
            Categoria parent = new Categoria();
            parent.setId(request.parentId());
            categoria.setParent(parent);
        }

        Categoria guardada = categoriaRepository.save(categoria);
        return new CategoriaResponse(
                guardada.getId(),
                guardada.getNombre(),
                guardada.getTipo().name(),
                guardada.getIcono(),
                guardada.getColor()
        );
    }
}
