package com.proyecto.tramites.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pide un par nuevo de tokens a cambio de un token de renovacion valido (de un solo uso). */
public record RenovarRequest(@NotBlank @Size(max = 200) String refreshToken) {

	/** El token nunca debe salir en un log, ni por accidente. */
	@Override
	public String toString() {
		return "RenovarRequest[refreshToken=***]";
	}

}
