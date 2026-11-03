package cl.finanzas.personales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AsistenteChatRequest(
        @NotBlank @Size(max = 2000) String mensaje,
        @Size(max = 12) List<@NotNull @Valid Mensaje> historial,
        @NotNull UUID solicitudId
) {
    public AsistenteChatRequest(String mensaje, List<Mensaje> historial) { this(mensaje, historial, null); }
    public record Mensaje(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 4000) String content
    ) {}
}
