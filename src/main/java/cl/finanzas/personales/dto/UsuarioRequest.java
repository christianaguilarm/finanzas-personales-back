package cl.finanzas.personales.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 120) String nombre,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}

