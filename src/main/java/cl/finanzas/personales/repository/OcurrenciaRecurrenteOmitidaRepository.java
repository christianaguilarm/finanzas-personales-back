package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.OcurrenciaRecurrenteOmitida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OcurrenciaRecurrenteOmitidaRepository extends JpaRepository<OcurrenciaRecurrenteOmitida, Long> {
    List<OcurrenciaRecurrenteOmitida> findByUser_IdAndPeriodo(Long userId, String periodo);

    boolean existsByUser_IdAndClaveRecurrenteAndPeriodo(Long userId, String claveRecurrente, String periodo);

    void deleteByUser_IdAndClaveRecurrenteAndPeriodo(Long userId, String claveRecurrente, String periodo);
}
