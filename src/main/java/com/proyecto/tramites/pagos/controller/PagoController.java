package com.proyecto.tramites.pagos.controller;

import com.proyecto.tramites.comun.util.Actor;
import com.proyecto.tramites.pagos.dto.PagoResponse;
import com.proyecto.tramites.pagos.dto.ValidacionPagoRequest;
import com.proyecto.tramites.pagos.entity.EstadoPago;
import com.proyecto.tramites.pagos.service.PagoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** El controlador solo recibe, valida y responde; la logica vive en PagoService. */
@RestController
@RequestMapping("/api")
public class PagoController {

	private final PagoService servicio;

	public PagoController(PagoService servicio) {
		this.servicio = servicio;
	}

	/**
	 * Permiso: pago:crear. Sube el comprobante (campo "comprobante", multipart/form-data: PDF, JPG o PNG de
	 * hasta 5 MB). Solo para un tramite en POR_PAGAR y sin otro pago pendiente.
	 */
	@PostMapping("/tramites/{id}/pagos")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('pago:crear')")
	public PagoResponse crear(@PathVariable Long id, @RequestPart("comprobante") MultipartFile comprobante,
			Authentication autenticacion) {
		return servicio.crear(Actor.de(autenticacion), id, comprobante);
	}

	/** Permiso: pago:leer. Propios o todos segun el alcance del rol. Filtros: estado y tramiteId. */
	@GetMapping("/pagos")
	@PreAuthorize("hasAuthority('pago:leer')")
	public Page<PagoResponse> listar(@RequestParam(required = false) EstadoPago estado,
			@RequestParam(required = false) Long tramiteId,
			@PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable paginacion,
			Authentication autenticacion) {
		return servicio.listar(Actor.de(autenticacion), estado, tramiteId, paginacion);
	}

	/** Permiso: pago:leer. Un pago ajeno responde 404. */
	@GetMapping("/pagos/{id}")
	@PreAuthorize("hasAuthority('pago:leer')")
	public PagoResponse obtener(@PathVariable Long id, Authentication autenticacion) {
		return servicio.obtener(Actor.de(autenticacion), id);
	}

	/** Permiso: pago:leer. Descarga el comprobante tal como se subio. */
	@GetMapping("/pagos/{id}/comprobante")
	@PreAuthorize("hasAuthority('pago:leer')")
	public ResponseEntity<byte[]> comprobante(@PathVariable Long id, Authentication autenticacion) {
		PagoService.Comprobante comprobante = servicio.comprobante(Actor.de(autenticacion), id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(comprobante.tipoMime()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("comprobante-" + id + extension(comprobante.tipoMime()))
								.build().toString())
				.body(comprobante.contenido());
	}

	/**
	 * Permiso: pago:editar. El gestor valida o rechaza un pago pendiente. Validar finaliza el tramite.
	 * Quien registro el pago no puede resolverlo (403).
	 */
	@PostMapping("/pagos/{id}/validacion")
	@PreAuthorize("hasAuthority('pago:editar')")
	public PagoResponse validar(@PathVariable Long id, @Valid @RequestBody ValidacionPagoRequest solicitud,
			Authentication autenticacion) {
		return servicio.resolver(Actor.de(autenticacion), id, solicitud);
	}

	private static String extension(String tipoMime) {
		return switch (tipoMime) {
			case "application/pdf" -> ".pdf";
			case "image/png" -> ".png";
			case "image/jpeg" -> ".jpg";
			default -> "";
		};
	}

}
