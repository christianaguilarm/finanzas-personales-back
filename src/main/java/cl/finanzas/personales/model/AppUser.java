package cl.finanzas.personales.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_user_seq")
    @SequenceGenerator(name = "app_user_seq", sequenceName = "app_user_id_seq", allocationSize = 1)
    private Long id;

    /**
     * Identidad real del usuario: el claim 'sub' del access token de Auth0.
     *
     * <p>Es la UNICA clave por la que este backend reconoce a alguien. El email es un dato de
     * perfil: nunca se usa para vincular ni para buscar usuarios.
     *
     * <p>Nullable a proposito: las filas creadas antes de la migracion siguen en NULL, y
     * {@code ddl-auto=update} no puebla columnas nuevas en bases que ya tienen datos. Esas filas
     * se vinculan a mano con un UPDATE (ver sql/V1__auth0_setup.sql).
     */
    @Column(name = "auth0_sub", unique = true, length = 64)
    private String auth0Sub;

    /**
     * Dato de perfil, no clave de identidad: no lleva UNIQUE a proposito. El indice
     * {@code ix_app_user_email} (no unico) es solo de busqueda.
     */
    @Column(nullable = false, length = 150)
    private String email;

    /**
     * 300, no 120: el claim 'name' de Auth0 puede venir mas largo (login social). Ojo: la entidad
     * manda sobre la base con ddl-auto=update; si aqui bajara a 120, Hibernate revertiria la
     * columna a varchar(120) en el siguiente arranque.
     */
    @Column(length = 300)
    private String nombre;

    /**
     * Nullable y siempre en null. No hay login local que compare contrasenas (la autenticacion
     * vive en Auth0), asi que escribir un bcrypt inservible solo ocuparia espacio y mintiria
     * sobre el modelo de seguridad. La columna quedo nullable en sql/V1__auth0_setup.sql.
     */
    @Column(name = "password_hash", columnDefinition = "TEXT")
    private String passwordHash;

    /**
     * {@code @Builder.Default} es obligatorio: sin el, Lombok descarta el inicializador de campo
     * al usar {@code @Builder} y {@code AppUser.builder().build()} deja esto en null, violando el
     * NOT NULL.
     */
    @Builder.Default
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Cuenta> cuentas = new HashSet<>();
}