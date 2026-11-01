package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.TelegramUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface TelegramUpdateRepository extends JpaRepository<TelegramUpdate, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from TelegramUpdate e where e.id = :id")
    Optional<TelegramUpdate> bloquearPorId(@Param("id") Long id);

    @Query(value = """
            select e.* from telegram_update e where e.procesado = false
              and not exists (select 1 from telegram_update a
                  where a.actor_id = e.actor_id and a.id < e.id and a.procesado = false)
            order by e.id limit 1 for update skip locked
            """, nativeQuery = true)
    Optional<TelegramUpdate> siguienteParaProcesar();

    @Query(value = """
            select e.* from telegram_update e
            where e.procesado = true and e.entregado = false and e.fallos < 5 and e.proximo_intento <= :ahora
              and not exists (select 1 from telegram_update a where a.actor_id = e.actor_id
                  and a.id < e.id and a.entregado = false and a.fallos < 5)
            order by e.id limit 1 for update skip locked
            """, nativeQuery = true)
    Optional<TelegramUpdate> siguienteParaEntregar(@Param("ahora") Instant ahora);
}
