package com.proyecto.tramites.ingresos.dto;

import com.proyecto.tramites.ingresos.entity.Ingreso;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Lo que la API devuelve de un ingreso. La entidad nunca sale por la API. */
public record IngresoResponse(Long id, LocalDate fecha, BigDecimal montoGtq, String descripcion, Instant creadoEn,
		Instant actualizadoEn) {

	public static IngresoResponse de(Ingreso ingreso) {
		return new IngresoResponse(ingreso.getId(), ingreso.getFecha(), ingreso.getMontoGtq(),
				ingreso.getDescripcion(), ingreso.getCreadoEn(), ingreso.getActualizadoEn());
	}

}
