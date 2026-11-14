package cl.finanzas.personales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.YearMonth;

@Entity
@Table(name = "ocurrencia_recurrente_omitida",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_omision_recurrente_usuario_clave_periodo",
                columnNames = {"user_id", "clave_recurrente", "periodo"}),
        indexes = @Index(name = "ix_omision_recurrente_usuario_periodo", columnList = "user_id, periodo"))
@Getter
@Setter
public class OcurrenciaRecurrenteOmitida {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ocurrencia_recurrente_omitida_seq")
    @SequenceGenerator(name = "ocurrencia_recurrente_omitida_seq",
            sequenceName = "ocurrencia_recurrente_omitida_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "clave_recurrente", nullable = false, length = 64)
    private String claveRecurrente;

    @Column(name = "periodo", nullable = false, length = 7)
    private String periodo;

    protected OcurrenciaRecurrenteOmitida() {
    }

    public OcurrenciaRecurrenteOmitida(AppUser user, String claveRecurrente, YearMonth periodo) {
        this.user = user;
        this.claveRecurrente = claveRecurrente;
        this.periodo = periodo.toString();
    }
}
