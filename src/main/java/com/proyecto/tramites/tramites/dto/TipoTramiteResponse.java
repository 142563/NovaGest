package com.proyecto.tramites.tramites.dto;

import com.proyecto.tramites.tramites.entity.TipoTramite;
import java.math.BigDecimal;
import java.util.List;

/** Lo que la API devuelve de un tipo de tramite, con los campos que declara. Las entidades nunca salen por la API. */
public record TipoTramiteResponse(Long id, String nombre, String descripcion, BigDecimal monto, boolean activo,
		List<CampoTipoResponse> campos) {

	public static TipoTramiteResponse de(TipoTramite tipo) {
		List<CampoTipoResponse> campos = tipo.getCampos().stream().map(CampoTipoResponse::de).toList();
		return new TipoTramiteResponse(tipo.getId(), tipo.getNombre(), tipo.getDescripcion(), tipo.getMonto(),
				tipo.isActivo(), campos);
	}

}
