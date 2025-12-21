package cl.finanzas.personales.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "comercio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comercio {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comercio_seq")
    @SequenceGenerator(name = "comercio_seq", sequenceName = "comercio_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 80)
    private String rubro;

    @OneToMany(mappedBy = "comercio", fetch = FetchType.LAZY)
    private Set<Transaccion> transacciones = new HashSet<>();
}