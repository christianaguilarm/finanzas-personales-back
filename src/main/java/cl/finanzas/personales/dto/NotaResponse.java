package cl.finanzas.personales.dto;

import java.time.LocalDate;

public record NotaResponse(Long id, String texto, LocalDate fechaLimite, boolean resuelta, Long diasRestantes) {}
