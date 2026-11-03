package cl.finanzas.personales.configuration;

import cl.finanzas.personales.service.telegram.NotaAvisoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotaAvisoWorker {
    private final NotaAvisoService avisos;

    @Scheduled(fixedDelay = 900000)
    public void encolar() {
        try { avisos.encolarDelDia(); }
        catch (RuntimeException e) { log.warn("No se pudieron encolar avisos de notas; se reintentará"); }
    }

    @Scheduled(fixedDelay = 60000)
    public void entregar() {
        try { for (int i = 0; i < 20 && avisos.entregarSiguiente(); i++) { /* drenar avisos */ } }
        catch (RuntimeException e) { log.warn("No se pudieron entregar avisos de notas; se reintentará"); }
    }
}
