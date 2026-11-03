package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.Nota;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotaRepository extends JpaRepository<Nota, Long> {
    List<Nota> findByUserId(Long userId);
    Optional<Nota> findByIdAndUserId(Long id, Long userId);
    Optional<Nota> findByUserIdAndSolicitudId(Long userId, UUID solicitudId);
    List<Nota> findByResueltaFalseAndFechaLimiteLessThanEqual(LocalDate fecha);
    List<Nota> findByUserIdAndResueltaFalseAndFechaLimiteLessThanEqual(Long userId, LocalDate fecha);
}
