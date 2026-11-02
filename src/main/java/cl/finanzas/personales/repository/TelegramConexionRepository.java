package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.TelegramConexion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TelegramConexionRepository extends JpaRepository<TelegramConexion, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TelegramConexion c where c.userId = :userId")
    Optional<TelegramConexion> bloquearPorUsuario(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TelegramConexion c where c.codigoHash = :hash")
    Optional<TelegramConexion> bloquearPorCodigo(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TelegramConexion c where c.telegramUserId = :actor")
    Optional<TelegramConexion> bloquearPorTelegram(@Param("actor") Long actor);

    boolean existsByTelegramUserId(Long telegramUserId);
    Optional<TelegramConexion> findByTelegramUserId(Long telegramUserId);
}
