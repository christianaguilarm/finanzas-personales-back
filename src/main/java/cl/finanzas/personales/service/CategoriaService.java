package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.CategoriaRequest;
import cl.finanzas.personales.dto.CategoriaResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.Categoria;
import cl.finanzas.personales.repository.CategoriaRepository;
import cl.finanzas.personales.repository.TransaccionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;
    private final TransaccionRepository transaccionRepository;

    public List<CategoriaResponse> obtenerCategoriasPorUsuario(Long userId) {
        return categoriaRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoriaResponse crearCategoria(Long userId, CategoriaRequest request) {
        Categoria categoria = new Categoria();
        categoria.setNombre(request.nombre());
        categoria.setTipo(request.tipo());
        categoria.setIcono(request.icono());
        categoria.setColor(request.color());
        categoria.setParent(resolverPadre(request, userId, null));

        AppUser user = new AppUser();
        user.setId(userId);
        categoria.setUser(user);

        return toResponse(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaResponse actualizarCategoria(Long id, Long userId, CategoriaRequest request) {
        Categoria categoria = obtenerCategoriaDelUsuario(id, userId);
        categoria.setNombre(request.nombre());
        categoria.setTipo(request.tipo());
        categoria.setIcono(request.icono());
        categoria.setColor(request.color());
        categoria.setParent(resolverPadre(request, userId, categoria));

        return toResponse(categoriaRepository.save(categoria));
    }

    @Transactional(readOnly = true)
    public CategoriaResponse obtenerCategoria(Long id, Long userId) {
        return toResponse(obtenerCategoriaDelUsuario(id, userId));
    }

    @Transactional
    public void eliminarCategoria(Long id, Long userId) {
        Categoria categoria = obtenerCategoriaDelUsuario(id, userId);

        List<Categoria> subcategorias = categoriaRepository.findByParentId(id);
        if (!subcategorias.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede eliminar porque tiene " + subcategorias.size() + " subcategoría(s) asociada(s)"
            );
        }

        long transacciones =
                transaccionRepository.countByCategoriaId(id) + transaccionRepository.countBySubcategoriaId(id);
        if (transacciones > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede eliminar porque tiene " + transacciones + " transacción(es) asociada(s)"
            );
        }

        categoriaRepository.delete(categoria);
    }

    private Categoria obtenerCategoriaDelUsuario(Long id, Long userId) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La categoria no existe"));

        if (!userId.equals(categoria.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La categoria no pertenece al usuario");
        }

        return categoria;
    }

    private Categoria resolverPadre(CategoriaRequest request, Long userId, Categoria categoria) {
        if (request.parentId() == null) {
            return null;
        }

        if (categoria != null && request.parentId().equals(categoria.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una categoria no puede ser su propia categoria padre");
        }

        Categoria padre = categoriaRepository.findById(request.parentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoria padre no existe"));

        if (!userId.equals(padre.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoria padre no pertenece al usuario");
        }

        if (padre.getTipo() != request.tipo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoria padre debe ser del mismo tipo");
        }

        if (categoria != null && esDescendienteDe(padre, categoria.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede asignar una subcategoria como categoria padre");
        }

        return padre;
    }

    private boolean esDescendienteDe(Categoria categoria, Long idBuscado) {
        Categoria actual = categoria;
        while (actual.getParent() != null) {
            actual = actual.getParent();
            if (actual.getId().equals(idBuscado)) {
                return true;
            }
        }
        return false;
    }

    private CategoriaResponse toResponse(Categoria categoria) {
        return new CategoriaResponse(
                categoria.getId(),
                categoria.getNombre(),
                categoria.getTipo().name(),
                categoria.getIcono(),
                categoria.getColor(),
                categoria.getParent() != null ? categoria.getParent().getId() : null
        );
    }
}
