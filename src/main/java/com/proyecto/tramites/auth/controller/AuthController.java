package com.proyecto.tramites.auth.controller;

import com.proyecto.tramites.auth.dto.LoginRequest;
import com.proyecto.tramites.auth.dto.RegistroRequest;
import com.proyecto.tramites.auth.dto.TokenResponse;
import com.proyecto.tramites.auth.dto.UsuarioResponse;
import com.proyecto.tramites.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El controlador solo recibe, valida y responde; la logica vive en AuthService. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService servicio;

	public AuthController(AuthService servicio) {
		this.servicio = servicio;
	}

	/** Publico: crea un ciudadano. */
	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioResponse registro(@Valid @RequestBody RegistroRequest solicitud) {
		return servicio.registrar(solicitud);
	}

	/** Publico: devuelve el token de acceso (JWT) y el de renovacion. */
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest solicitud) {
		return servicio.iniciarSesion(solicitud);
	}

}
