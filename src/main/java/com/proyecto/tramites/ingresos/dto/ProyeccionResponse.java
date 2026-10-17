package com.proyecto.tramites.ingresos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tarjeta 22: ingresos propios proyectados a otra moneda con el tipo de cambio del Banguat.
 *
 * @param moneda codigo ISO de la moneda destino
 * @param nombreMoneda nombre legible
 * @param desde filtro de fecha de los ingresos (null = sin limite)
 * @param hasta filtro de fecha de los ingresos (null = sin limite)
 * @param cantidadIngresos cuantos ingresos se sumaron
 * @param totalGtq suma de los ingresos en quetzales
 * @param totalEnMoneda total convertido, redondeado a dos decimales
 * @param fechaSolicitada fecha del tipo de cambio pedida (por defecto hoy)
 * @param quetzalesPorDolar tipo de cambio del dolar usado
 * @param fechaDolar fecha real de ese valor
 * @param valorMoneda valor de la moneda destino tal como lo publica el Banguat (null si es el dolar)
 * @param fechaMoneda fecha real de ese valor (null si es el dolar)
 * @param convencion como se cotiza la moneda destino
 * @param fuente de donde salieron los tipos de cambio
 * @param estimado verdadero si el Banguat no respondio y se uso el ultimo valor guardado
 */
public record ProyeccionResponse(String moneda, String nombreMoneda, LocalDate desde, LocalDate hasta,
		int cantidadIngresos, BigDecimal totalGtq, BigDecimal totalEnMoneda, LocalDate fechaSolicitada,
		BigDecimal quetzalesPorDolar, LocalDate fechaDolar, BigDecimal valorMoneda, LocalDate fechaMoneda,
		String convencion, String fuente, boolean estimado) {
}
