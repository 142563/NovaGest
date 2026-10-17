package com.proyecto.tramites.divisas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila de TipoCambioRango o TipoCambioRangoMoneda. El valor viene tal como lo publica el Banguat:
 * su significado depende de la moneda (ver {@link ConvencionCotizacion}). Venta y compra pueden diferir.
 * TipoCambioRangoMoneda omite los dias sin cotizacion (fines de semana).
 */
public record CotizacionMoneda(int moneda, LocalDate fecha, BigDecimal venta, BigDecimal compra) {
}
