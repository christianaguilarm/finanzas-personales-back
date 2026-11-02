package cl.finanzas.personales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class TelegramConexion {
    @Id
    private Long userId;
    @Column(unique = true)
    private Long telegramUserId;
    private Long chatId;
    @Column(length = 64)
    private String telegramUsername;
    private UUID vinculoId;
    private Instant vinculadoEn;
    @Column(length = 64, unique = true)
    private String codigoHash;
    private Instant codigoExpiraEn;
    @Column(nullable = false, length = 50000)
    private String historial = "[]";
    private Instant historialActualizadoEn;
}
