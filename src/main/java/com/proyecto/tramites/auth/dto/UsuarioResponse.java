package com.proyecto.tramites.auth.dto;

import com.proyecto.tramites.usuarios.entity.Rol;
import com.proyecto.tramites.usuarios.entity.Usuario;
import java.util.List;

/** Lo que la API devuelve de un usuario. Las entidades JPA nunca salen por la API. */
public record UsuarioResponse(Long id, String correo, String nombre, List<String> roles) {

	public static UsuarioResponse de(Usuario usuario) {
		List<String> roles = usuario.getRoles().stream().map(Rol::getNombre).sorted().toList();
		return new UsuarioResponse(usuario.getId(), usuario.getCorreo(), usuario.getNombre(), roles);
	}

}
