package cl.finanzas.personales.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cuenta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cuenta_seq")
    @SequenceGenerator(name = "cuenta_seq", sequenceName = "cuenta_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, length = 3)
    private String moneda = "CLP";

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "saldo_inicial", nullable = false, precision = 14, scale = 2)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "cuenta", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Transaccion> transacciones = new HashSet<>();
}
