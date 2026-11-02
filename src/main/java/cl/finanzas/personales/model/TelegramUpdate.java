package cl.finanzas.personales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/** Bandeja durable: se confirma el webhook antes de consultar a la IA. */
@Entity
@Getter
@Setter
public class TelegramUpdate {
    @Id
    private Long id;
    @Column(nullable = false)
    private Long actorId;
    private UUID vinculoId;
    @Column(nullable = false, length = 20000)
    private String contenido;
    @Column(nullable = false)
    private Instant recibidoEn;
    @Column(nullable = false)
    private boolean procesado;
    @Column(nullable = false)
    private boolean entregado;
    @Column(nullable = false, length = 64000)
    private String respuestas = "[]";
    @Column(nullable = false)
    private int enviados;
    @Column(nullable = false)
    private int fallos;
    @Column(nullable = false)
    private Instant proximoIntento;
}
