package com.proyecto.tramites.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos para registrarse como ciudadano. La clave admite hasta 72 caracteres, el limite de BCrypt. */
public record RegistroRequest(
		@NotBlank @Size(max = 120) String nombre,
		@NotBlank @Email @Size(max = 254) String correo,
		@NotBlank @Size(min = 8, max = 72) String clave) {

	/** La clave nunca debe salir en un log, ni por accidente. */
	@Override
	public String toString() {
		return "RegistroRequest[correo=" + correo + ", clave=***]";
	}

}
