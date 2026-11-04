package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.PresupuestoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PresupuestoCategoriaRepository extends JpaRepository<PresupuestoCategoria, Long> {
    List<PresupuestoCategoria> findByUserId(Long userId);
    Optional<PresupuestoCategoria> findByUserIdAndCategoriaId(Long userId, Long categoriaId);
}
