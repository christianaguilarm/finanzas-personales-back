package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.NotaAviso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface NotaAvisoRepository extends JpaRepository<NotaAviso, UUID> {
    @Modifying
    @Query("delete from NotaAviso a where a.nota.id = :notaId")
    void eliminarPorNota(@Param("notaId") Long notaId);

    @Modifying
    @Query(value = """
            insert into nota_aviso (id, nota_id, fecha_aviso, vinculo_id, chat_id, entregado, fallos, proximo_intento)
            select :id, :notaId, :fecha, :vinculo, :chat, false, 0, :ahora
            where not exists (select 1 from nota_aviso where nota_id = :notaId and fecha_aviso = :fecha)
            """, nativeQuery = true)
    int encolar(@Param("id") UUID id, @Param("notaId") Long notaId, @Param("fecha") LocalDate fecha,
                @Param("vinculo") UUID vinculo, @Param("chat") Long chat, @Param("ahora") Instant ahora);

    @Query(value = """
            select a.* from nota_aviso a where a.entregado = false and a.fallos < 5
              and a.proximo_intento <= :ahora order by a.fecha_aviso, a.id
            limit 1 for update skip locked
            """, nativeQuery = true)
    Optional<NotaAviso> siguiente(@Param("ahora") Instant ahora);
}
