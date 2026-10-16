package com.proyecto.tramites.auth.dto;

/** Par de tokens que entrega el login: uno de acceso (corto) y uno de renovacion (largo, de un solo uso). */
public record TokenResponse(String accessToken, String refreshToken, String tipo, long expiraEnSegundos) {

	/** Los tokens nunca deben salir en un log. */
	@Override
	public String toString() {
		return "TokenResponse[tipo=" + tipo + ", expiraEnSegundos=" + expiraEnSegundos + ", tokens=***]";
	}

}
