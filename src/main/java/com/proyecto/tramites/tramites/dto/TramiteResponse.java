package com.proyecto.tramites.tramites.dto;

import com.proyecto.tramites.tramites.entity.EstadoTramite;
import com.proyecto.tramites.tramites.entity.TransicionTramite;
import com.proyecto.tramites.tramites.entity.Tramite;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Lo que la API devuelve de un tramite. Las entidades JPA nunca salen por la API.
 * destinosPosibles lista a que estados puede pasar segun su estado actual (vacio si ya es final);
 * quien puede hacerlo lo decide el servidor al pedir la transicion.
 */
public record TramiteResponse(Long id, Long tipoId, String tipoNombre, Long ciudadanoId, Long gestorId,
		EstadoTramite estado, Map<String, Object> datos, String motivo, List<EstadoTramite> destinosPosibles,
		Instant creadoEn, Instant actualizadoEn) {

	public static TramiteResponse de(Tramite tramite, Map<String, Object> datos) {
		return new TramiteResponse(tramite.getId(), tramite.getTipoTramite().getId(),
				tramite.getTipoTramite().getNombre(), tramite.getCiudadanoId(), tramite.getGestorId(),
				tramite.getEstado(), datos, tramite.getMotivo(), TransicionTramite.destinosDesde(tramite.getEstado()),
				tramite.getCreadoEn(), tramite.getActualizadoEn());
	}

}
