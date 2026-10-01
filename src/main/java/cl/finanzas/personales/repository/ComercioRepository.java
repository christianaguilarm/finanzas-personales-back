package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.Comercio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComercioRepository extends JpaRepository<Comercio, Long> {
    boolean existsByIdAndUserId(Long id, Long userId);
}

