package com.proyecto.tramites;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo existe en las pruebas: un endpoint por permiso para comprobar que @PreAuthorize responde 403
 * a quien tiene token pero no el permiso. Los endpoints reales los declaran sus tarjetas.
 */
@RestController
class ControladorDePruebaSeguridad {

	@GetMapping("/api/_prueba/solo-administrador")
	@PreAuthorize("hasAuthority('usuario:eliminar')")
	String soloAdministrador() {
		return "ok";
	}

	@GetMapping("/api/_prueba/crear-tramite")
	@PreAuthorize("hasAuthority('tramite:crear')")
	String crearTramite() {
		return "ok";
	}

}
