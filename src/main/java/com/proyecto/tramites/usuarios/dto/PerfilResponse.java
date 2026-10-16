package com.proyecto.tramites.usuarios.dto;

import com.proyecto.tramites.usuarios.entity.Rol;
import com.proyecto.tramites.usuarios.entity.Usuario;
import java.util.List;

/**
 * Perfil de quien hace la peticion, con los permisos que tiene ahora mismo.
 * alcanceTotal indica si sus roles ven los registros de todos (true) o solo los propios (false).
 */
public record PerfilResponse(Long id, String correo, String nombre, List<String> roles, List<String> permisos,
		boolean alcanceTotal) {

	public static PerfilResponse de(Usuario usuario) {
		List<String> roles = usuario.getRoles().stream().map(Rol::getNombre).sorted().toList();
		List<String> permisos = usuario.permisosEfectivos().stream().sorted().toList();
		return new PerfilResponse(usuario.getId(), usuario.getCorreo(), usuario.getNombre(), roles, permisos,
				usuario.tieneAlcanceTotal());
	}

}
