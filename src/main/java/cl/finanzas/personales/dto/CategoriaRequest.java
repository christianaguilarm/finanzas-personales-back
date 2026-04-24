package cl.finanzas.personales.dto;

import cl.finanzas.personales.model.TipoTransaccion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 80) String nombre,
        @NotNull TipoTransaccion tipo,
        Long parentId,
        @Size(max = 40) String icono,
        @Size(max = 10) String color
) {
}

