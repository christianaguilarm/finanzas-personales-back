package cl.finanzas.personales.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "nota", uniqueConstraints = @UniqueConstraint(name = "uk_nota_usuario_solicitud", columnNames = {"user_id", "solicitud_id"}))
@Getter @Setter
public class Nota {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nota_seq")
    @SequenceGenerator(name = "nota_seq", sequenceName = "nota_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 1000)
    private String texto;

    private LocalDate fechaLimite;

    @Column(nullable = false)
    private boolean resuelta;

    @Column(name = "solicitud_id")
    private UUID solicitudId;
}
