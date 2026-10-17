package com.proyecto.tramites.ingresos.controller;

import com.proyecto.tramites.comun.util.Actor;
import com.proyecto.tramites.ingresos.dto.IngresoRequest;
import com.proyecto.tramites.ingresos.dto.IngresoResponse;
import com.proyecto.tramites.ingresos.dto.ProyeccionResponse;
import com.proyecto.tramites.ingresos.service.IngresoService;
import com.proyecto.tramites.ingresos.service.ProyeccionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarjeta 19: ingresos propios. El controlador solo recibe, valida y responde; la logica vive en el servicio.
 * Siempre trabaja sobre los ingresos de quien hace la peticion: un ingreso ajeno responde 404.
 */
@RestController
@RequestMapping("/api/ingresos")
public class IngresoController {

	private final IngresoService servicio;
	private final ProyeccionService proyecciones;

	public IngresoController(IngresoService servicio, ProyeccionService proyecciones) {
		this.servicio = servicio;
		this.proyecciones = proyecciones;
	}

	/**
	 * Permiso: ingreso:leer. Solo los propios. Orden: fecha, montoGtq, creadoEn, id. Filtros opcionales desde y
	 * hasta (fecha del ingreso AAAA-MM-DD, ambas incluidas).
	 */
	@GetMapping
	@PreAuthorize("hasAuthority('ingreso:leer')")
	public Page<IngresoResponse> listar(@RequestParam(required = false) String desde,
			@RequestParam(required = false) String hasta,
			@PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable paginacion,
			Authentication autenticacion) {
		return servicio.listar(Actor.de(autenticacion).id(), desde, hasta, paginacion);
	}

	/**
	 * Permiso: ingreso:leer. Tarjeta 22: proyecta la suma de los ingresos propios a otra moneda (codigo ISO, ver
	 * /api/divisas/monedas) con el tipo de cambio del Banguat de la fecha indicada (por defecto hoy). Filtros
	 * opcionales desde y hasta sobre la fecha de los ingresos. Si el Banguat no responde, usa el ultimo valor
	 * guardado y marca la respuesta como estimada.
	 */
	@GetMapping("/proyeccion")
	@PreAuthorize("hasAuthority('ingreso:leer')")
	public ProyeccionResponse proyeccion(@RequestParam(required = false) String moneda,
			@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
			@RequestParam(required = false) String fecha, Authentication autenticacion) {
		return proyecciones.proyectar(Actor.de(autenticacion).id(), moneda, desde, hasta, fecha);
	}

	/** Permiso: ingreso:leer. Un ingreso ajeno responde 404. */
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('ingreso:leer')")
	public IngresoResponse obtener(@PathVariable Long id, Authentication autenticacion) {
		return servicio.obtener(Actor.de(autenticacion).id(), id);
	}

	/** Permiso: ingreso:crear. Queda a nombre de quien lo registra. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('ingreso:crear')")
	public IngresoResponse crear(@Valid @RequestBody IngresoRequest solicitud, Authentication autenticacion) {
		return servicio.crear(Actor.de(autenticacion).id(), solicitud);
	}

	/** Permiso: ingreso:editar. Reemplaza fecha, monto y descripcion. */
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ingreso:editar')")
	public IngresoResponse actualizar(@PathVariable Long id, @Valid @RequestBody IngresoRequest solicitud,
			Authentication autenticacion) {
		return servicio.actualizar(Actor.de(autenticacion).id(), id, solicitud);
	}

	/** Permiso: ingreso:eliminar. Borrado logico. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('ingreso:eliminar')")
	public void eliminar(@PathVariable Long id, Authentication autenticacion) {
		servicio.eliminar(Actor.de(autenticacion).id(), id);
	}

}
