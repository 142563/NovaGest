package com.proyecto.tramites.tramites.dto;

import com.proyecto.tramites.tramites.entity.EstadoTramite;
import com.proyecto.tramites.tramites.entity.TramiteHistorial;
import java.time.Instant;

/** Una fila del historial. El estado anterior es nulo en la primera: la creacion del tramite. */
public record HistorialResponse(Long id, EstadoTramite estadoAnterior, EstadoTramite estadoNuevo, Long actorId,
		String comentario, Instant fecha) {

	public static HistorialResponse de(TramiteHistorial fila) {
		return new HistorialResponse(fila.getId(), fila.getEstadoAnterior(), fila.getEstadoNuevo(), fila.getActorId(),
				fila.getComentario(), fila.getCreadoEn());
	}

}
