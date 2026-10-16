package com.proyecto.tramites.tramites.dto;

import com.proyecto.tramites.tramites.entity.CampoTipoTramite;
import com.proyecto.tramites.tramites.entity.TipoDato;
import java.math.BigDecimal;

public record CampoTipoResponse(String clave, String etiqueta, TipoDato tipoDato, boolean obligatorio,
		String patron, BigDecimal minimo, int orden) {

	public static CampoTipoResponse de(CampoTipoTramite campo) {
		return new CampoTipoResponse(campo.getClave(), campo.getEtiqueta(), campo.getTipoDato(),
				campo.isObligatorio(), campo.getPatron(), campo.getMinimo(), campo.getOrden());
	}

}
