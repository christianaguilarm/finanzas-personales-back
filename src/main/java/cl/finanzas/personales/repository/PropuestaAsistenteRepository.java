package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.PropuestaAsistente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PropuestaAsistenteRepository extends JpaRepository<PropuestaAsistente, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PropuestaAsistente p where p.id = :id and p.userId = :userId")
    Optional<PropuestaAsistente> buscarParaConfirmar(@Param("id") UUID id, @Param("userId") Long userId);
}
