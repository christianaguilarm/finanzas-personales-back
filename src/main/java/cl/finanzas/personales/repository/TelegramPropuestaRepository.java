package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.TelegramPropuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TelegramPropuestaRepository extends JpaRepository<TelegramPropuesta, UUID> {}
