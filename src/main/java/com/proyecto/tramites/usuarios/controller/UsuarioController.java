package com.proyecto.tramites.usuarios.controller;

import com.proyecto.tramites.usuarios.dto.PerfilResponse;
import com.proyecto.tramites.usuarios.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

	private final UsuarioService servicio;

	public UsuarioController(UsuarioService servicio) {
		this.servicio = servicio;
	}

	/** Solo exige estar autenticado: devuelve el perfil de quien hace la peticion. */
	@GetMapping("/yo")
	public PerfilResponse yo(Authentication autenticacion) {
		return servicio.perfilDe(Long.valueOf(autenticacion.getName()));
	}

}
