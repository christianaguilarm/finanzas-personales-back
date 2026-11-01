package cl.finanzas.personales.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.asistente")
@Getter
@Setter
public class AsistenteProperties {
    private boolean enabled = true;
    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-flash";

    public boolean disponible() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
