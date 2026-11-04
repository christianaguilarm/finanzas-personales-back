package cl.finanzas.personales.dto;

import java.math.BigDecimal;

public record PresupuestoCategoriaResponse(Long categoriaId, String categoriaNombre, BigDecimal montoMensual) {}
