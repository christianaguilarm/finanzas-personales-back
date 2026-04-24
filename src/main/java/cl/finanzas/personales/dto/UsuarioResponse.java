package cl.finanzas.personales.dto;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String email,
        String nombre,
        LocalDateTime creadoEn
) {
}

