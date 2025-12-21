package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {
    List<Cuenta> findByUserIdAndActivoIsTrue(Long userId);
}
