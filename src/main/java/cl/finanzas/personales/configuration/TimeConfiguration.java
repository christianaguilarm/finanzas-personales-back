package cl.finanzas.personales.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfiguration {

    /**
     * Permite preguntar por el periodo en curso desde los servicios sin acoplarlo a la hora
     * del sistema, para que la proyección del mes actual se pueda probar de forma determinista.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
