package com.proyecto.tramites.auth.service;

import com.proyecto.tramites.auth.entity.TokenRenovacion;
import com.proyecto.tramites.auth.repository.TokenRenovacionRepository;
import com.proyecto.tramites.comun.config.JwtProperties;
import com.proyecto.tramites.usuarios.entity.Rol;
import com.proyecto.tramites.usuarios.entity.Usuario;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emite los dos tokens del login. Los permisos NO viajan en el token: se resuelven en el servidor. */
@Service
public class TokenService {

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private final JwtEncoder codificador;
	private final JwtProperties propiedades;
	private final TokenRenovacionRepository renovaciones;
	private final Clock reloj;

	public TokenService(JwtEncoder codificador, JwtProperties propiedades,
			TokenRenovacionRepository renovaciones, Clock reloj) {
		this.codificador = codificador;
		this.propiedades = propiedades;
		this.renovaciones = renovaciones;
		this.reloj = reloj;
	}

	/** JWT HS256 con: id de usuario, roles, emision, vencimiento e identificador unico. */
	public String emitirAcceso(Usuario usuario) {
		Instant ahora = reloj.instant().truncatedTo(ChronoUnit.SECONDS);
		JwtClaimsSet datos = JwtClaimsSet.builder()
				.subject(String.valueOf(usuario.getId()))
				.issuedAt(ahora)
				.expiresAt(ahora.plus(Duration.ofMinutes(propiedades.accessMinutes())))
				.id(UUID.randomUUID().toString())
				.claim("roles", usuario.getRoles().stream().map(Rol::getNombre).sorted().toList())
				.build();
		return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), datos))
				.getTokenValue();
	}

	/** Valor aleatorio de 256 bits. Se entrega una sola vez; en la base queda solo su hash. */
	public String emitirRenovacion(Usuario usuario) {
		byte[] bytes = new byte[32];
		ALEATORIO.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant vence = reloj.instant().plus(Duration.ofDays(propiedades.refreshDays()));
		renovaciones.save(new TokenRenovacion(usuario, hash(token), vence));
		return token;
	}

	public long segundosDeVidaDelAcceso() {
		return Duration.ofMinutes(propiedades.accessMinutes()).toSeconds();
	}

	/** SHA-256 en hexadecimal (64 caracteres). */
	public static String hash(String token) {
		try {
			byte[] resumen = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(resumen);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

}
