package com.proyecto.tramites.divisas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tipo de cambio de referencia del dolar: quetzales por 1 dolar en esa fecha.
 * TipoCambioRango repite el ultimo valor en sabados y domingos.
 */
public record TipoCambioDolar(LocalDate fecha, BigDecimal referencia) {
}
