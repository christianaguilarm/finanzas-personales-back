package cl.finanzas.personales.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "nota_aviso", uniqueConstraints = @UniqueConstraint(name = "uk_nota_aviso_dia", columnNames = {"nota_id", "fecha_aviso"}))
@Getter @Setter
public class NotaAviso {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_id", nullable = false)
    private Nota nota;
    @Column(nullable = false)
    private LocalDate fechaAviso;
    @Column(nullable = false)
    private UUID vinculoId;
    @Column(nullable = false)
    private Long chatId;
    @Column(nullable = false)
    private boolean entregado;
    @Column(nullable = false)
    private int fallos;
    @Column(nullable = false)
    private Instant proximoIntento;
}
