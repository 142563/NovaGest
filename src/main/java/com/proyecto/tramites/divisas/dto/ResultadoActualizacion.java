package com.proyecto.tramites.divisas.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Tarjeta 21: resultado de la actualizacion diaria de tipos de cambio.
 *
 * @param fecha dia que se actualizo
 * @param actualizadas monedas con valor confirmado por el Banguat
 * @param estimadas monedas sin respuesta del Banguat que quedaron con su ultimo valor guardado
 * @param sinDatos monedas sin respuesta del Banguat y sin ningun valor guardado
 */
public record ResultadoActualizacion(LocalDate fecha, List<String> actualizadas, List<String> estimadas,
		List<String> sinDatos) {

	public boolean completa() {
		return estimadas.isEmpty() && sinDatos.isEmpty();
	}

}
