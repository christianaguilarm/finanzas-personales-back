package cl.finanzas.personales.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "presupuesto_categoria", uniqueConstraints = @UniqueConstraint(
        name = "uk_presupuesto_categoria_usuario", columnNames = {"user_id", "categoria_id"}))
@Getter
@Setter
public class PresupuestoCategoria {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "presupuesto_categoria_seq")
    @SequenceGenerator(name = "presupuesto_categoria_seq", sequenceName = "presupuesto_categoria_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "monto_mensual", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoMensual;
}
