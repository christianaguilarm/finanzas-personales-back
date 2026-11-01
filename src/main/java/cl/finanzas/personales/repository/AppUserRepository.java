package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.id = :id")
    Optional<AppUser> bloquearPorId(@Param("id") Long id);

    /**
     * Unica forma de resolver un usuario en este backend: por el claim 'sub' del JWT.
     *
     * <p>No existe (ni debe existir) un finder por email: el email es un dato de perfil y no
     * identifica a nadie. Ver UsuarioActualService.
     */
    Optional<AppUser> findByAuth0Sub(String auth0Sub);
}
