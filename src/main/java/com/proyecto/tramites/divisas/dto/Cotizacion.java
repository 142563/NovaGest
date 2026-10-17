package com.proyecto.tramites.divisas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un tipo de cambio listo para usar.
 *
 * @param fecha fecha del valor (puede ser anterior a la pedida: fin de semana, feriado o Banguat caido)
 * @param valor tal como lo publica el Banguat
 * @param fuente de donde salio el valor
 * @param estimado verdadero si el Banguat no respondio y se uso el ultimo valor guardado
 */
public record Cotizacion(LocalDate fecha, BigDecimal valor, String fuente, boolean estimado) {
}
