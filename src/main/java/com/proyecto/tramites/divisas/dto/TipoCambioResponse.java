package com.proyecto.tramites.divisas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tipo de cambio de una moneda para una fecha, con su fuente y si es estimado. */
public record TipoCambioResponse(String moneda, LocalDate fechaSolicitada, LocalDate fecha, BigDecimal valor,
		String convencion, String fuente, boolean estimado) {
}
