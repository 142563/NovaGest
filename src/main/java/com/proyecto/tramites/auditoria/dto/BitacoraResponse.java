package com.proyecto.tramites.auditoria.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Una fila de la bitacora tal como la ve quien audita. El correo de quien actuo sale enmascarado
 * (a***@dominio.com): sirve para ubicar a la persona sin exponer el dato completo.
 */
public record BitacoraResponse(Long id, Long actorId, String actorCorreo, String accion, String recurso,
		Long recursoId, Map<String, Object> antes, Map<String, Object> despues, String ip, Instant fecha) {

}
