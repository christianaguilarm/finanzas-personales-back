package cl.finanzas.personales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/** El contenido aprobado vive en el servidor: el cliente solo confirma su identificador. */
@Entity
@Getter
@Setter
public class PropuestaAsistente {
    @Id
    private UUID id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 12000)
    private String contenido;
    @Column(nullable = false)
    private Instant expiraEn;
    private Long transaccionId;
}
