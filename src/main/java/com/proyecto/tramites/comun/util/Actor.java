package com.proyecto.tramites.comun.util;

import org.springframework.security.core.Authentication;

/**
 * Quien hace la peticion, tal como lo resuelve el filtro del token: su id y si su rol tiene alcance
 * sobre todos los registros. El servicio compara el dueno del dato con el id; el codigo nunca pregunta
 * por el nombre de un rol.
 */
public record Actor(Long id, boolean alcanceTotal) {

	/** Autoridad que el filtro agrega cuando algun rol del usuario tiene alcance sobre todos los registros. */
	public static final String AUTORIDAD_ALCANCE_TOTAL = "alcance:todos";

	public static Actor de(Authentication autenticacion) {
		boolean total = autenticacion.getAuthorities().stream()
				.anyMatch(a -> AUTORIDAD_ALCANCE_TOTAL.equals(a.getAuthority()));
		return new Actor(Long.valueOf(autenticacion.getName()), total);
	}

}
