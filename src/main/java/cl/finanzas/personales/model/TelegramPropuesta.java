package cl.finanzas.personales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/** Un callback solo puede actuar sobre propuestas enviadas durante este vínculo de Telegram. */
@Entity
@Getter
@Setter
public class TelegramPropuesta {
    @Id
    private UUID id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Long telegramUserId;
    @Column(nullable = false)
    private UUID vinculoId;
    @Column(nullable = false)
    private boolean descartada;
}
