package com.proyecto.tramites.auth.service;

import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Corre despues de que Spring valida firma y vencimiento del token. Los permisos NO viajan en el token:
 * aqui se buscan en la base en cada peticion, asi que quitar un rol o desactivar a alguien aplica de inmediato.
 * Si el usuario ya no existe, esta eliminado, inactivo o bloqueado, el token deja de servir (401).
 */
@Component
public class JwtAutenticacionConverter implements Converter<Jwt, JwtAuthenticationToken> {

	private final UsuarioRepository usuarios;
	private final Clock reloj;

	public JwtAutenticacionConverter(UsuarioRepository usuarios, Clock reloj) {
		this.usuarios = usuarios;
		this.reloj = reloj;
	}

	@Override
	public JwtAuthenticationToken convert(Jwt jwt) {
		Usuario usuario = idDe(jwt)
				.flatMap(usuarios::findConRolesYPermisosByIdAndEliminadoEnIsNull)
				.filter(u -> u.isActivo() && !u.estaBloqueado(reloj.instant()))
				.orElseThrow(() -> new InvalidBearerTokenException("Token no valido"));
		List<GrantedAuthority> permisos = usuario.permisosEfectivos().stream()
				.sorted()
				.<GrantedAuthority>map(SimpleGrantedAuthority::new)
				.toList();
		// El nombre del principal es el id del usuario: los servicios lo comparan con el dueno del dato
		return new JwtAuthenticationToken(jwt, permisos, String.valueOf(usuario.getId()));
	}

	private static java.util.Optional<Long> idDe(Jwt jwt) {
		try {
			return java.util.Optional.of(Long.valueOf(jwt.getSubject()));
		}
		catch (NumberFormatException e) {
			return java.util.Optional.empty();
		}
	}

}
