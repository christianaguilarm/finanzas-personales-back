package cl.finanzas.personales.service.telegram;

import cl.finanzas.personales.configuration.TelegramProperties;
import cl.finanzas.personales.model.Nota;
import cl.finanzas.personales.repository.NotaAvisoRepository;
import cl.finanzas.personales.repository.NotaRepository;
import cl.finanzas.personales.repository.TelegramConexionRepository;
import cl.finanzas.personales.service.NotaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class NotaAvisoService {
    private final NotaRepository notas;
    private final NotaAvisoRepository avisos;
    private final TelegramConexionRepository conexiones;
    private final TelegramApiClient api;
    private final TelegramProperties telegram;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    public NotaAvisoService(NotaRepository notas, NotaAvisoRepository avisos, TelegramConexionRepository conexiones,
                           TelegramApiClient api, TelegramProperties telegram, ObjectMapper mapper, Clock clock,
                           PlatformTransactionManager manager) {
        this.notas = notas;
        this.avisos = avisos;
        this.conexiones = conexiones;
        this.api = api;
        this.telegram = telegram;
        this.mapper = mapper;
        this.clock = clock;
        tx = new TransactionTemplate(manager);
    }

    @Transactional
    public void encolarDelDia() {
        if (!telegram.disponible() || LocalTime.now(clock.withZone(NotaService.ZONA)).isBefore(LocalTime.of(9, 0))) return;
        LocalDate hoy = LocalDate.now(clock.withZone(NotaService.ZONA));
        for (Nota nota : notas.findByResueltaFalseAndFechaLimiteLessThanEqual(hoy.plusDays(2))) {
            var conexion = conexiones.findById(nota.getUser().getId()).orElse(null);
            if (conexion == null || conexion.getChatId() == null || conexion.getVinculoId() == null) continue;
            avisos.encolar(UUID.randomUUID(), nota.getId(), hoy, conexion.getVinculoId(), conexion.getChatId(), clock.instant());
        }
    }

    public boolean entregarSiguiente() {
        if (!telegram.disponible()) return false;
        return Boolean.TRUE.equals(tx.execute(status -> {
            var aviso = avisos.siguiente(clock.instant()).orElse(null);
            if (aviso == null) return false;
            var nota = aviso.getNota();
            LocalDate hoy = LocalDate.now(clock.withZone(NotaService.ZONA));
            var conexion = conexiones.findById(nota.getUser().getId()).orElse(null);
            if (!hoy.equals(aviso.getFechaAviso()) || nota.isResuelta() || nota.getFechaLimite() == null
                    || nota.getFechaLimite().isAfter(hoy.plusDays(2)) || conexion == null
                    || !aviso.getVinculoId().equals(conexion.getVinculoId())
                    || !aviso.getChatId().equals(conexion.getChatId())) {
                aviso.setEntregado(true);
                return true;
            }
            long dias = ChronoUnit.DAYS.between(hoy, nota.getFechaLimite());
            String plazo = dias > 0 ? "Te quedan " + dias + (dias == 1 ? " día" : " días")
                    : dias == 0 ? "Vence hoy" : "Venció hace " + (-dias) + ((-dias) == 1 ? " día" : " días");
            String texto = "Recordatorio: " + nota.getTexto() + "\nFecha límite: " + nota.getFechaLimite() + " · " + plazo + ".";
            try {
                api.enviar("sendMessage", mapper.valueToTree(Map.of("chat_id", aviso.getChatId(), "text", texto)));
                aviso.setEntregado(true);
            } catch (TelegramApiClient.TelegramApiException e) {
                aviso.setFallos(aviso.getFallos() + 1);
                aviso.setProximoIntento(clock.instant().plusSeconds(30L * aviso.getFallos()));
            }
            return true;
        }));
    }
}
