package com.proyecto.tramites.tramites.controller;

import com.proyecto.tramites.tramites.dto.TipoTramiteRequest;
import com.proyecto.tramites.tramites.dto.TipoTramiteResponse;
import com.proyecto.tramites.tramites.service.TipoTramiteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El controlador solo recibe, valida y responde; la logica vive en el servicio. */
@RestController
@RequestMapping("/api/tipos-tramite")
public class TipoTramiteController {

	private static final String PERMISO_EDITAR = "tipo_tramite:editar";

	private final TipoTramiteService servicio;

	public TipoTramiteController(TipoTramiteService servicio) {
		this.servicio = servicio;
	}

	/** Permiso: tipo_tramite:leer. Parametros: page, size (maximo 100), sort (nombre, monto, creadoEn) y activo. */
	@GetMapping
	@PreAuthorize("hasAuthority('tipo_tramite:leer')")
	public Page<TipoTramiteResponse> listar(@RequestParam(required = false) Boolean activo,
			@PageableDefault(size = 20, sort = "nombre") Pageable paginacion, Authentication autenticacion) {
		return servicio.listar(puedeEditar(autenticacion), activo, paginacion);
	}

	/** Permiso: tipo_tramite:leer. */
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('tipo_tramite:leer')")
	public TipoTramiteResponse obtener(@PathVariable Long id, Authentication autenticacion) {
		return servicio.obtener(id, puedeEditar(autenticacion));
	}

	/** Permiso: tipo_tramite:crear. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('tipo_tramite:crear')")
	public TipoTramiteResponse crear(@Valid @RequestBody TipoTramiteRequest solicitud) {
		return servicio.crear(solicitud);
	}

	/** Permiso: tipo_tramite:editar. */
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('tipo_tramite:editar')")
	public TipoTramiteResponse actualizar(@PathVariable Long id, @Valid @RequestBody TipoTramiteRequest solicitud) {
		return servicio.actualizar(id, solicitud);
	}

	/** Permiso: tipo_tramite:eliminar. Borrado logico. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('tipo_tramite:eliminar')")
	public void eliminar(@PathVariable Long id) {
		servicio.eliminar(id);
	}

	private static boolean puedeEditar(Authentication autenticacion) {
		return autenticacion.getAuthorities().stream().anyMatch(a -> PERMISO_EDITAR.equals(a.getAuthority()));
	}

}
