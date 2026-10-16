package com.proyecto.tramites.usuarios.service;

import com.proyecto.tramites.usuarios.dto.PerfilResponse;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsuarioService {

	private final UsuarioRepository usuarios;

	public UsuarioService(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	@Transactional(readOnly = true)
	public PerfilResponse perfilDe(Long idUsuario) {
		return usuarios.findConRolesYPermisosByIdAndEliminadoEnIsNull(idUsuario)
				.map(PerfilResponse::de)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

}
