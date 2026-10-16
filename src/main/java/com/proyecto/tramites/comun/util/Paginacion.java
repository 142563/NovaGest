package com.proyecto.tramites.comun.util;

import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Utilidades para los listados paginados (page, size, sort) de toda la API. */
public final class Paginacion {

	private Paginacion() {
	}

	/**
	 * Solo se puede ordenar por campos permitidos: un campo inexistente daria un error interno y uno
	 * sensible expondria datos. Si no se cumple, 400.
	 */
	public static Pageable validarOrden(Pageable paginacion, Set<String> permitidos) {
		for (Sort.Order orden : paginacion.getSort()) {
			if (!permitidos.contains(orden.getProperty())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Orden no permitido. Se puede ordenar por: " + String.join(", ", permitidos.stream().sorted().toList()));
			}
		}
		return paginacion;
	}

}
