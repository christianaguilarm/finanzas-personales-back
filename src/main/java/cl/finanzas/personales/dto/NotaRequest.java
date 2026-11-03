package cl.finanzas.personales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record NotaRequest(@NotBlank @Size(max = 1000) String texto, LocalDate fechaLimite, boolean resuelta) {}
