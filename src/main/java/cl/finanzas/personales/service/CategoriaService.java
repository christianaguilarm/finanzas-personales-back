package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.CategoriaRequest;
import cl.finanzas.personales.dto.CategoriaResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.Categoria;
import cl.finanzas.personales.repository.CategoriaRepository;
import cl.finanzas.personales.repository.AppUserRepository;
import cl.finanzas.personales.repository.TransaccionRepository;
import cl.finanzas.personales.repository.PresupuestoCategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import cl.finanzas.personales.model.TipoTransaccion;

@Service
@RequiredArgsConstructor
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;
    private final AppUserRepository appUserRepository;
    private final TransaccionRepository transaccionRepository;
    private final PresupuestoCategoriaRepository presupuestoCategoriaRepository;

    @Transactional
    public List<CategoriaResponse> obtenerCategoriasPorUsuario(Long userId) {
        asegurarCategoriasPrestamo(userId);
        return categoriaRepository.findByUserId(userId)
                .stream()
                .filter(Categoria::isActivo)
                .map(this::toResponse)
                .toList();
    }

    private void asegurarCategoriasPrestamo(Long userId) {
        appUserRepository.bloquearPorId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe"));
        List<Categoria> categorias = categoriaRepository.findByUserId(userId);
        for (TipoTransaccion tipo : List.of(TipoTransaccion.EGRESO, TipoTransaccion.INGRESO)) {
            String codigo = tipo == TipoTransaccion.EGRESO ? "PRESTAMO_EGRESO" : "PRESTAMO_INGRESO";
            if (categorias.stream().anyMatch(c -> codigo.equals(c.getCodigoSistema()))) continue;
            Categoria existente = categorias.stream()
                    .filter(c -> c.getTipo() == tipo && c.getParent() == null)
                    .filter(c -> c.getNombre().equalsIgnoreCase("Prestado") || c.getNombre().equalsIgnoreCase("Prestamo")
                            || c.getNombre().equalsIgnoreCase("Préstamo"))
                    .findFirst().orElse(null);
            if (existente == null) {
                existente = new Categoria();
                AppUser user = new AppUser();
                user.setId(userId);
                existente.setUser(user);
                existente.setTipo(tipo);
                existente.setNombre("Préstamo");
            }
            existente.setCodigoSistema(codigo);
            categoriaRepository.save(existente);
        }
    }

    @Transactional
    public CategoriaResponse crearCategoria(Long userId, CategoriaRequest request) {
        Categoria categoria = new Categoria();
        categoria.setNombre(request.nombre());
        categoria.setTipo(request.tipo());
        categoria.setIcono(request.icono());
        categoria.setColor(request.color());
        categoria.setParent(resolverPadre(request, userId, null));
        categoria.setClasificacionGasto(clasificacion(request, categoria.getParent()));

        AppUser user = new AppUser();
        user.setId(userId);
        categoria.setUser(user);

        return toResponse(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaResponse actualizarCategoria(Long id, Long userId, CategoriaRequest request) {
        Categoria categoria = obtenerCategoriaDelUsuario(id, userId);
        if (categoria.getCodigoSistema() != null && (categoria.getTipo() != request.tipo() || request.parentId() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría Préstamo debe mantener su tipo y no puede ser subcategoría");
        }
        categoria.setNombre(request.nombre());
        categoria.setTipo(request.tipo());
        categoria.setIcono(request.icono());
        categoria.setColor(request.color());
        categoria.setParent(resolverPadre(request, userId, categoria));
        categoria.setClasificacionGasto(clasificacion(request, categoria.getParent()));

        return toResponse(categoriaRepository.save(categoria));
    }

    @Transactional(readOnly = true)
    public CategoriaResponse obtenerCategoria(Long id, Long userId) {
        return toResponse(obtenerCategoriaDelUsuario(id, userId));
    }

    @Transactional
    public void eliminarCategoria(Long id, Long userId) {
        Categoria categoria = obtenerCategoriaDelUsuario(id, userId);

        if (categoria.getCodigoSistema() != null && categoria.getCodigoSistema().startsWith("PRESTAMO_")) {
            categoriaRepository.findByUserId(userId).stream()
                    .filter(c -> c.getCodigoSistema() != null && c.getCodigoSistema().startsWith("PRESTAMO_"))
                    .forEach(c -> c.setActivo(false));
            return;
        }

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

        presupuestoCategoriaRepository.findByUserIdAndCategoriaId(userId, id)
                .ifPresent(presupuestoCategoriaRepository::delete);

        categoriaRepository.delete(categoria);
    }

    @Transactional
    public void reactivarCategoriasPrestamo(Long userId) {
        asegurarCategoriasPrestamo(userId);
        categoriaRepository.findByUserId(userId).stream()
                .filter(c -> c.getCodigoSistema() != null && c.getCodigoSistema().startsWith("PRESTAMO_"))
                .forEach(c -> c.setActivo(true));
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
                categoria.getParent() != null ? categoria.getParent().getId() : null,
                categoria.getCodigoSistema(),
                clasificacionEfectiva(categoria) != null ? clasificacionEfectiva(categoria).name() : null
        );
    }

    private cl.finanzas.personales.model.ClasificacionGasto clasificacion(
            CategoriaRequest request, Categoria parent) {
        if (request.tipo() != TipoTransaccion.EGRESO) return null;
        if (parent != null) return clasificacionEfectiva(parent);
        return request.clasificacionGasto() != null ? request.clasificacionGasto()
                : cl.finanzas.personales.model.ClasificacionGasto.NECESIDAD;
    }

    private cl.finanzas.personales.model.ClasificacionGasto clasificacionEfectiva(Categoria categoria) {
        if (categoria.getTipo() != TipoTransaccion.EGRESO) return null;
        if (categoria.getParent() != null) return clasificacionEfectiva(categoria.getParent());
        return categoria.getClasificacionGasto() != null ? categoria.getClasificacionGasto()
                : cl.finanzas.personales.model.ClasificacionGasto.NECESIDAD;
    }
}
