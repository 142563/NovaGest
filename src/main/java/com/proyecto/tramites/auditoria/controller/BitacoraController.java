package com.proyecto.tramites.auditoria.controller;

import com.proyecto.tramites.auditoria.dto.BitacoraResponse;
import com.proyecto.tramites.auditoria.dto.FiltroBitacora;
import com.proyecto.tramites.auditoria.service.BitacoraConsultaService;
import com.proyecto.tramites.comun.util.Actor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la bitacora de acciones. Solo existe GET: nadie crea, edita ni borra registros por la API
 * (la bitacora la escribe el propio sistema al ejecutar cada accion).
 */
@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {

	private final BitacoraConsultaService servicio;

	public BitacoraController(BitacoraConsultaService servicio) {
		this.servicio = servicio;
	}

	/**
	 * Permiso: bitacora:leer (auditor y administrador). Filtros opcionales: recurso, recursoId, accion, actorId,
	 * desde y hasta (AAAA-MM-DD, hora de Guatemala). Orden: id, creadoEn, accion, recurso. Cada consulta queda
	 * registrada.
	 */
	@GetMapping
	@PreAuthorize("hasAuthority('bitacora:leer')")
	public Page<BitacoraResponse> consultar(@RequestParam(required = false) String recurso,
			@RequestParam(required = false) Long recursoId, @RequestParam(required = false) String accion,
			@RequestParam(required = false) Long actorId, @RequestParam(required = false) String desde,
			@RequestParam(required = false) String hasta,
			@PageableDefault(size = 20, sort = { "creadoEn", "id" }, direction = Sort.Direction.DESC) Pageable paginacion,
			Authentication autenticacion) {
		FiltroBitacora filtro = FiltroBitacora.de(recurso, recursoId, accion, actorId, desde, hasta);
		return servicio.consultar(Actor.de(autenticacion), filtro, paginacion);
	}

}
