package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.TelegramUpdate;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class TelegramRegistro {
    private final EntityManager entityManager;

    /** INSERT estricto. Un update repetido nunca debe hacer merge y reiniciar un evento procesado. */
    @Transactional
    public void insertar(TelegramUpdate update) {
        entityManager.persist(update);
        entityManager.flush();
    }
}
