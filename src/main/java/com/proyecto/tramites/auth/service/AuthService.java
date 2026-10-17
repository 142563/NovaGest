package com.proyecto.tramites.auth.service;

import com.proyecto.tramites.auditoria.service.BitacoraService;
import com.proyecto.tramites.auth.dto.LoginRequest;
import com.proyecto.tramites.auth.dto.LogoutRequest;
import com.proyecto.tramites.auth.dto.RegistroRequest;
import com.proyecto.tramites.auth.dto.RenovarRequest;
import com.proyecto.tramites.auth.dto.TokenResponse;
import com.proyecto.tramites.auth.dto.UsuarioResponse;
import com.proyecto.tramites.auth.entity.TokenRenovacion;
import com.proyecto.tramites.auth.repository.TokenRenovacionRepository;
import com.proyecto.tramites.comun.config.LoginProperties;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.RolRepository;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registro, inicio de sesion, renovacion de tokens y cierre de sesion.
 * Controles de este servicio: el mismo mensaje 401 para cualquier fallo de credenciales; bloqueo de la cuenta
 * tras varios fallos seguidos; token de renovacion de un solo uso (rota en cada canje) y revocacion de todos
 * los del usuario si se presenta uno ya usado, que es la senal de que alguien lo copio.
 */
@Service
public class AuthService {

	private static final String ROL_CIUDADANO = "CIUDADANO";

	/** Un solo mensaje para correo inexistente, clave incorrecta, cuenta inactiva o bloqueada. */
	static final String MENSAJE_CREDENCIALES = "Credenciales invalidas";

	/** Un solo mensaje para cualquier token de renovacion que no sirva: inventado, usado, revocado o vencido. */
	static final String MENSAJE_RENOVACION = "Token de renovacion invalido";

	private final UsuarioRepository usuarios;
	private final RolRepository roles;
	private final PasswordEncoder codificador;
	private final TokenService tokens;
	private final Clock reloj;
	private final TokenRenovacionRepository renovaciones;
	private final BitacoraService bitacora;
	private final LoginProperties login;

	/** Hash de relleno: se compara aunque el correo no exista, para que ambos casos tarden lo mismo. */
	private final String hashDeRelleno;

	public AuthService(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder codificador,
			TokenService tokens, Clock reloj, TokenRenovacionRepository renovaciones, BitacoraService bitacora,
			LoginProperties login) {
		this.usuarios = usuarios;
		this.roles = roles;
		this.codificador = codificador;
		this.tokens = tokens;
		this.reloj = reloj;
		this.renovaciones = renovaciones;
		this.bitacora = bitacora;
		this.login = login;
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
		// El correo no se guarda en la bitacora: se ubica al usuario por su id
		bitacora.registrar(usuario.getId(), "REGISTRO_USUARIO", "usuario", usuario.getId(), null,
				Map.of("rol", ROL_CIUDADANO));
		return UsuarioResponse.de(usuario);
	}

	/**
	 * Valida credenciales y entrega el token de acceso y el de renovacion. Cada clave incorrecta suma un fallo;
	 * al llegar al maximo la cuenta se bloquea el tiempo configurado (5 fallos y 15 minutos segun el plan).
	 * Un inicio correcto reinicia la cuenta. El fallo se guarda aunque la respuesta sea un error (noRollbackFor).
	 */
	@Transactional(noRollbackFor = ResponseStatusException.class)
	public TokenResponse iniciarSesion(LoginRequest solicitud) {
		Usuario usuario = usuarios.bloquearPorCorreo(normalizar(solicitud.correo())).orElse(null);
		String hash = usuario != null ? usuario.getHashContrasena() : hashDeRelleno;
		boolean claveCorrecta = codificador.matches(solicitud.clave(), hash);
		Instant ahora = reloj.instant();
		if (usuario == null) {
			throw credencialesInvalidas();
		}
		boolean bloqueada = usuario.estaBloqueado(ahora);
		if (!claveCorrecta) {
			if (usuario.isActivo() && !bloqueada) {
				registrarFallo(usuario, ahora);
			}
			throw credencialesInvalidas();
		}
		if (!usuario.isActivo() || bloqueada) {
			throw credencialesInvalidas();
		}
		usuario.setIntentosFallidos(0);
		usuario.setBloqueadoHasta(null);
		usuarios.saveAndFlush(usuario);
		return emitirPar(usuario);
	}

	/**
	 * Cambia un token de renovacion por un par nuevo. El token es de un solo uso: al canjearlo queda marcado y
	 * se entrega otro. Si alguien presenta uno ya usado, se asume que fue copiado y se revocan TODOS los
	 * tokens de renovacion de ese usuario. La cuenta debe seguir activa y sin bloqueo.
	 */
	@Transactional(noRollbackFor = ResponseStatusException.class)
	public TokenResponse renovar(RenovarRequest solicitud) {
		Instant ahora = reloj.instant();
		TokenRenovacion token = renovaciones.bloquearPorHash(TokenService.hash(solicitud.refreshToken()))
				.orElseThrow(AuthService::renovacionInvalida);
		Long idUsuario = token.getUsuario().getId();
		if (token.getUsadoEn() != null) {
			renovaciones.revocarTodosDe(idUsuario, ahora);
			bitacora.registrar(null, "REUSO_TOKEN_RENOVACION", "usuario", idUsuario, null,
					Map.of("tokensRevocados", "todos"));
			throw renovacionInvalida();
		}
		if (token.getRevocadoEn() != null || token.estaVencido(ahora)) {
			throw renovacionInvalida();
		}
		Usuario usuario = usuarios.findConRolesYPermisosByIdAndEliminadoEnIsNull(idUsuario)
				.filter(u -> u.isActivo() && !u.estaBloqueado(ahora))
				.orElseThrow(AuthService::renovacionInvalida);
		token.marcarUsado(ahora);
		renovaciones.saveAndFlush(token);
		return emitirPar(usuario);
	}

	/**
	 * Cierra la sesion. Con el token de renovacion revoca solo ese (si es del propio usuario); sin el, revoca
	 * todos los del usuario. Un token que no existe o es de otro se ignora sin avisar. El token de acceso ya
	 * emitido sigue siendo valido hasta que venza (15 minutos): la API no guarda sesiones.
	 */
	@Transactional
	public void cerrarSesion(Long idUsuario, LogoutRequest solicitud) {
		Instant ahora = reloj.instant();
		String refresco = solicitud == null || solicitud.refreshToken() == null || solicitud.refreshToken().isBlank()
				? null : solicitud.refreshToken().trim();
		if (refresco == null) {
			int revocados = renovaciones.revocarTodosDe(idUsuario, ahora);
			bitacora.registrar(idUsuario, "CIERRE_SESION", "usuario", idUsuario, null,
					Map.of("alcance", "todas", "sesionesCerradas", revocados));
			return;
		}
		renovaciones.bloquearPorHash(TokenService.hash(refresco))
				.filter(t -> t.getUsuario().getId().equals(idUsuario) && t.getRevocadoEn() == null)
				.ifPresent(t -> {
					t.revocar(ahora);
					renovaciones.saveAndFlush(t);
					bitacora.registrar(idUsuario, "CIERRE_SESION", "usuario", idUsuario, null,
							Map.of("alcance", "una", "sesionesCerradas", 1));
				});
	}

	private TokenResponse emitirPar(Usuario usuario) {
		return new TokenResponse(tokens.emitirAcceso(usuario), tokens.emitirRenovacion(usuario), "Bearer",
				tokens.segundosDeVidaDelAcceso());
	}

	/** Suma un fallo. Al llegar al maximo bloquea la cuenta y reinicia el contador para el siguiente ciclo. */
	private void registrarFallo(Usuario usuario, Instant ahora) {
		int fallos = usuario.getIntentosFallidos() + 1;
		if (fallos >= login.maxIntentos()) {
			Instant hasta = ahora.plus(Duration.ofMinutes(login.bloqueoMinutos()));
			usuario.setIntentosFallidos(0);
			usuario.setBloqueadoHasta(hasta);
			usuarios.saveAndFlush(usuario);
			bitacora.registrar(null, "BLOQUEO_CUENTA", "usuario", usuario.getId(),
					Map.of("intentosFallidos", fallos - 1), Map.of("bloqueadoHasta", hasta.toString()));
		}
		else {
			usuario.setIntentosFallidos(fallos);
			usuarios.saveAndFlush(usuario);
		}
	}

	private static String normalizar(String correo) {
		return correo.trim().toLowerCase(Locale.ROOT);
	}

	private static ResponseStatusException correoEnUso() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya esta registrado");
	}

	private static ResponseStatusException credencialesInvalidas() {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAJE_CREDENCIALES);
	}

	private static ResponseStatusException renovacionInvalida() {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAJE_RENOVACION);
	}

}
