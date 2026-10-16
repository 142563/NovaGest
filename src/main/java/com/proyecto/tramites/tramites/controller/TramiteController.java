package com.proyecto.tramites.tramites.controller;

import com.proyecto.tramites.comun.util.Actor;
import com.proyecto.tramites.tramites.dto.TramiteActualizarRequest;
import com.proyecto.tramites.tramites.dto.TramiteRequest;
import com.proyecto.tramites.tramites.dto.TramiteResponse;
import com.proyecto.tramites.tramites.service.TramiteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El controlador solo recibe, valida y responde; la logica vive en el servicio. */
@RestController
@RequestMapping("/api/tramites")
public class TramiteController {

	private final TramiteService servicio;

	public TramiteController(TramiteService servicio) {
		this.servicio = servicio;
	}

	/** Permiso: tramite:leer. Propios o todos segun el alcance del rol. Orden: id, creadoEn, estado. */
	@GetMapping
	@PreAuthorize("hasAuthority('tramite:leer')")
	public Page<TramiteResponse> listar(
			@PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable paginacion,
			Authentication autenticacion) {
		return servicio.listar(Actor.de(autenticacion), paginacion);
	}

	/** Permiso: tramite:leer. Un tramite ajeno responde 404. */
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('tramite:leer')")
	public TramiteResponse obtener(@PathVariable Long id, Authentication autenticacion) {
		return servicio.obtener(Actor.de(autenticacion), id);
	}

	/** Permiso: tramite:crear. Abre el tramite en Borrador. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('tramite:crear')")
	public TramiteResponse crear(@Valid @RequestBody TramiteRequest solicitud, Authentication autenticacion) {
		return servicio.crear(Actor.de(autenticacion), solicitud);
	}

	/** Permiso: tramite:editar, mas la regla del estado y del dueno. Solo cambia los datos. */
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('tramite:editar')")
	public TramiteResponse actualizar(@PathVariable Long id, @Valid @RequestBody TramiteActualizarRequest solicitud,
			Authentication autenticacion) {
		return servicio.actualizar(Actor.de(autenticacion), id, solicitud);
	}

	/** Permiso: tramite:eliminar. Borrado logico. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('tramite:eliminar')")
	public void eliminar(@PathVariable Long id, Authentication autenticacion) {
		servicio.eliminar(Actor.de(autenticacion), id);
	}

}
