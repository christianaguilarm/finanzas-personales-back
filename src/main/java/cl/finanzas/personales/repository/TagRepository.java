package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
}
