package com.proyecto.tramites.auth.service;

import com.proyecto.tramites.auth.dto.LoginRequest;
import com.proyecto.tramites.auth.dto.RegistroRequest;
import com.proyecto.tramites.auth.dto.TokenResponse;
import com.proyecto.tramites.auth.dto.UsuarioResponse;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.RolRepository;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import java.time.Clock;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private static final String ROL_CIUDADANO = "CIUDADANO";

	/** Un solo mensaje para correo inexistente, clave incorrecta, cuenta inactiva o bloqueada. */
	static final String MENSAJE_CREDENCIALES = "Credenciales invalidas";

	private final UsuarioRepository usuarios;
	private final RolRepository roles;
	private final PasswordEncoder codificador;
	private final TokenService tokens;
	private final Clock reloj;

	/** Hash de relleno: se compara aunque el correo no exista, para que ambos casos tarden lo mismo. */
	private final String hashDeRelleno;

	public AuthService(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder codificador,
			TokenService tokens, Clock reloj) {
		this.usuarios = usuarios;
		this.roles = roles;
		this.codificador = codificador;
		this.tokens = tokens;
		this.reloj = reloj;
		this.hashDeRelleno = codificador.encode("relleno-sin-uso");
	}

	/** Registro publico: siempre crea un ciudadano. Los demas roles los asigna un administrador. */
	@Transactional
	public UsuarioResponse registrar(RegistroRequest solicitud) {
		String correo = normalizar(solicitud.correo());
		if (usuarios.existsByCorreoIgnoreCaseAndEliminadoEnIsNull(correo)) {
			throw correoEnUso();
		}
		Usuario usuario = new Usuario(correo, solicitud.nombre().trim(), codificador.encode(solicitud.clave()));
		usuario.getRoles().add(roles.findByNombre(ROL_CIUDADANO).orElseThrow(
				() -> new IllegalStateException("Falta el rol CIUDADANO: revisar la migracion V2")));
		try {
			usuarios.saveAndFlush(usuario);
		}
		catch (DataIntegrityViolationException e) {
			throw correoEnUso();
		}
		return UsuarioResponse.de(usuario);
	}

	/** Valida credenciales y entrega el token de acceso y el de renovacion. */
	@Transactional
	public TokenResponse iniciarSesion(LoginRequest solicitud) {
		Usuario usuario = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull(normalizar(solicitud.correo()))
				.orElse(null);
		String hash = usuario != null ? usuario.getHashContrasena() : hashDeRelleno;
		boolean claveCorrecta = codificador.matches(solicitud.clave(), hash);
		if (usuario == null || !claveCorrecta || !usuario.isActivo() || usuario.estaBloqueado(reloj.instant())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAJE_CREDENCIALES);
		}
		return new TokenResponse(tokens.emitirAcceso(usuario), tokens.emitirRenovacion(usuario), "Bearer",
				tokens.segundosDeVidaDelAcceso());
	}

	private static String normalizar(String correo) {
		return correo.trim().toLowerCase(Locale.ROOT);
	}

	private static ResponseStatusException correoEnUso() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya esta registrado");
	}

}
