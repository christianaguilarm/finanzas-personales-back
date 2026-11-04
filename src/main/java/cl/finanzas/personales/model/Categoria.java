package cl.finanzas.personales.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "categoria_seq")
    @SequenceGenerator(name = "categoria_seq", sequenceName = "categoria_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"cuentas", "categorias", "passwordHash"})
    private AppUser user;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(name = "codigo_sistema", length = 30)
    private String codigoSistema;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransaccion tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "clasificacion_gasto", length = 20)
    private ClasificacionGasto clasificacionGasto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Categoria parent;

    @Column(length = 40)
    private String icono;

    @Column(length = 10)
    private String color;

    @OneToMany(mappedBy = "categoria", fetch = FetchType.LAZY)
    private Set<Transaccion> transacciones = new HashSet<>();
}
