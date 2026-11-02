package cl.finanzas.personales.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.telegram")
@Getter
@Setter
public class TelegramProperties {
    private String botToken = "";
    private String botUsername = "";
    private String webhookSecret = "";
    private String apiBaseUrl = "https://api.telegram.org";

    public boolean disponible() {
        return botToken != null && botToken.matches("[0-9]+:[A-Za-z0-9_-]+")
                && botUsername != null && botUsername.matches("[A-Za-z0-9_]{5,32}")
                && botUsername.toLowerCase(java.util.Locale.ROOT).endsWith("bot")
                && webhookSecret != null && webhookSecret.matches("[A-Za-z0-9_-]{16,256}");
    }
}
