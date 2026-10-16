package com.proyecto.tramites.tramites.dto;

import com.proyecto.tramites.tramites.entity.TipoTramite;
import java.math.BigDecimal;

/** Lo que la API devuelve de un tipo de tramite. Las entidades JPA nunca salen por la API. */
public record TipoTramiteResponse(Long id, String nombre, String descripcion, BigDecimal monto, boolean activo) {

	public static TipoTramiteResponse de(TipoTramite tipo) {
		return new TipoTramiteResponse(tipo.getId(), tipo.getNombre(), tipo.getDescripcion(), tipo.getMonto(),
				tipo.isActivo());
	}

}
