package com.proyecto.tramites.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * Cierra la sesion. Si trae el token de renovacion, solo se revoca ese (cerrar una sesion); si no, se revocan
 * todos los del usuario (cerrar todas).
 */
public record LogoutRequest(@Size(max = 200) String refreshToken) {

	/** El token nunca debe salir en un log, ni por accidente. */
	@Override
	public String toString() {
		return "LogoutRequest[refreshToken=***]";
	}

}
