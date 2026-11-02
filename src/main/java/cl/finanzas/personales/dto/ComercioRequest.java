package cl.finanzas.personales.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ComercioRequest(@NotBlank @Size(max = 120) String nombre, @Size(max = 80) String rubro) {}
