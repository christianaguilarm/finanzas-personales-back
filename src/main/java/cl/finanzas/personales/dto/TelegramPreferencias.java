package cl.finanzas.personales.dto;

import jakarta.validation.constraints.Positive;

/** categoriaId puede ser una categoría raíz o una subcategoría de egreso. Null quita la preferencia. */
public record TelegramPreferencias(@Positive Long cuentaId, @Positive Long categoriaId) {}
