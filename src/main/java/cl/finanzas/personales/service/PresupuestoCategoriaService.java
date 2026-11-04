package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.PresupuestoCategoriaRequest;
import cl.finanzas.personales.dto.PresupuestoCategoriaResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.Categoria;
import cl.finanzas.personales.model.PresupuestoCategoria;
import cl.finanzas.personales.model.TipoTransaccion;
import cl.finanzas.personales.repository.CategoriaRepository;
import cl.finanzas.personales.repository.PresupuestoCategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PresupuestoCategoriaService {
    private final PresupuestoCategoriaRepository presupuestos;
    private final CategoriaRepository categorias;

    @Transactional(readOnly = true)
    public List<PresupuestoCategoriaResponse> listar(Long userId) {
        return presupuestos.findByUserId(userId).stream()
                .sorted(Comparator.comparing(p -> p.getCategoria().getNombre(), String.CASE_INSENSITIVE_ORDER))
                .map(this::respuesta).toList();
    }

    @Transactional
    public PresupuestoCategoriaResponse guardar(Long userId, Long categoriaId, PresupuestoCategoriaRequest request) {
        Categoria categoria = categorias.findById(categoriaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La categoría no existe"));
        if (!categoria.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La categoría no pertenece al usuario");
        }
        if (!categoria.isActivo() || categoria.getTipo() != TipoTransaccion.EGRESO || categoria.getParent() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El presupuesto requiere una categoría principal de gasto");
        }
        PresupuestoCategoria presupuesto = presupuestos.findByUserIdAndCategoriaId(userId, categoriaId)
                .orElseGet(() -> {
                    PresupuestoCategoria nuevo = new PresupuestoCategoria();
                    AppUser user = new AppUser(); user.setId(userId); nuevo.setUser(user);
                    nuevo.setCategoria(categoria); return nuevo;
                });
        presupuesto.setMontoMensual(request.montoMensual());
        return respuesta(presupuestos.save(presupuesto));
    }

    @Transactional
    public void eliminar(Long userId, Long categoriaId) {
        PresupuestoCategoria presupuesto = presupuestos.findByUserIdAndCategoriaId(userId, categoriaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El presupuesto no existe"));
        presupuestos.delete(presupuesto);
    }

    private PresupuestoCategoriaResponse respuesta(PresupuestoCategoria p) {
        return new PresupuestoCategoriaResponse(p.getCategoria().getId(), p.getCategoria().getNombre(), p.getMontoMensual());
    }
}
