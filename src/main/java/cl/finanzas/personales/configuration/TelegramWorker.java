package cl.finanzas.personales.configuration;

import cl.finanzas.personales.service.telegram.TelegramColaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class TelegramWorker {
    private final TelegramProperties properties;
    private final TelegramColaService cola;

    @Scheduled(fixedDelayString = "${app.telegram.poll-delay-ms:1000}")
    public void procesar() {
        if (!properties.disponible()) return;
        try {
            for (int i = 0; i < 8 && cola.entregarSiguiente(); i++) { /* drenar respuestas pendientes */ }
            cola.procesarSiguiente();
            for (int i = 0; i < 8 && cola.entregarSiguiente(); i++) { /* entregar sin esperar al próximo ciclo */ }
        } catch (RuntimeException e) {
            log.warn("El procesador de Telegram reintentará en el siguiente ciclo");
        }
    }
}
