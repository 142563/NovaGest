package com.proyecto.tramites.auth.controller;

import com.proyecto.tramites.auth.dto.LoginRequest;
import com.proyecto.tramites.auth.dto.LogoutRequest;
import com.proyecto.tramites.auth.dto.RegistroRequest;
import com.proyecto.tramites.auth.dto.RenovarRequest;
import com.proyecto.tramites.auth.dto.TokenResponse;
import com.proyecto.tramites.auth.dto.UsuarioResponse;
import com.proyecto.tramites.auth.service.AuthService;
import com.proyecto.tramites.comun.util.SoloAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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

	/** Publico: devuelve el token de acceso (JWT) y el de renovacion. 5 fallos seguidos bloquean la cuenta 15 minutos. */
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest solicitud) {
		return servicio.iniciarSesion(solicitud);
	}

	/**
	 * Publico, pero exige un token de renovacion valido: lo cambia por un par nuevo. El token es de un solo uso;
	 * presentar uno ya usado revoca todos los del usuario.
	 */
	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RenovarRequest solicitud) {
		return servicio.renovar(solicitud);
	}

	/**
	 * Exige sesion iniciada. Revoca el token de renovacion enviado (una sesion) o todos los del usuario si no
	 * se envia ninguno. El token de acceso sigue valido hasta vencer.
	 */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SoloAutenticado
	public void logout(@Valid @RequestBody(required = false) LogoutRequest solicitud, Authentication autenticacion) {
		servicio.cerrarSesion(Long.valueOf(autenticacion.getName()), solicitud);
	}

}
