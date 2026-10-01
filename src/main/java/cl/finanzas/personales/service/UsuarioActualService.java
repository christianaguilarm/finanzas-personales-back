package cl.finanzas.personales.service;

import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Resuelve el AppUser que hay detras de un access token de Auth0.
 *
 * <p>La identidad vive en Auth0 (claim 'sub'); aqui solo se busca la fila que le corresponde a
 * ese 'sub'. Si no existe se crea, y esa fila nace completamente vacia: sin cuentas y sin
 * categorias. No se compara por email en ningun momento, la vinculacion de usuarios es manual.
 */
@Service
@RequiredArgsConstructor
public class UsuarioActualService {

    private final AppUserRepository appUserRepository;
    private final Clock clock;

    /**
     * Usuario del token. Si su 'sub' es desconocido se provisiona al vuelo.
     */
    public AppUser usuarioActual(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No hay un token valido en la peticion");
        }
        return resolverOProvisionar(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name")
        );
    }

    /**
     * Atajo para los servicios que hoy reciben un {@code Long userId}: la identidad sale del
     * token, nunca de un path, un query param o un body.
     */
    public Long idActual(Jwt jwt) {
        return usuarioActual(jwt).getId();
    }

    AppUser resolverOProvisionar(String sub, String email, String nombre) {
        if (sub == null || sub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El token no trae el claim 'sub'");
        }

        return appUserRepository.findByAuth0Sub(sub)
                .orElseGet(() -> provisionar(sub, email, nombre));
    }

    private AppUser provisionar(String sub, String email, String nombre) {
        if (email == null || email.isBlank()) {
            // Sin email no hay fila: la columna es NOT NULL. Sin proveedor de email ni Actions
            // configurados, un token sin 'email' no se puede provisionar.
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "El token no trae el claim 'email', no se puede crear el usuario"
            );
        }

        AppUser nuevo = new AppUser();
        nuevo.setAuth0Sub(sub);
        nuevo.setEmail(email.trim().toLowerCase(Locale.ROOT));
        // El claim 'name' de Auth0 es una sola cadena: se guarda tal cual, sin inventar un
        // parseo de nombre/apellido.
        nuevo.setNombre(nombre);
        // El reloj inyectado, no LocalDateTime.now(): el Clock de TimeConfiguration es lo que
        // hace esto determinista en los tests.
        nuevo.setCreadoEn(LocalDateTime.now(clock));
        // password_hash queda en null a proposito (la columna es nullable): no hay login local
        // que compare contrasenas. Sin cuentas ni categorias: el primer ingreso arranca vacio.

        try {
            return appUserRepository.saveAndFlush(nuevo);
        } catch (DataIntegrityViolationException carrera) {
            // El unico UNIQUE de app_user es auth0_sub, asi que lo unico que puede haber fallado
            // aqui es la carrera de dos requests con el mismo 'sub' desconocido a la vez: gano el
            // primero que escribio y este perdio. Se usa la fila que quedo. Cualquier otro
            // conflicto de integridad (o de conexion) se propaga sin tocar nada.
            return appUserRepository.findByAuth0Sub(sub)
                    .orElseThrow(() -> carrera);
        }
    }
}