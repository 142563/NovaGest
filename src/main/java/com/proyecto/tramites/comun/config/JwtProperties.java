package com.proyecto.tramites.comun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de los tokens. La clave sale de la variable de entorno JWT_SECRET (nunca del codigo)
 * y debe tener al menos 32 caracteres (256 bits) para HS256.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, int accessMinutes, int refreshDays) {

	/** Nunca se imprime la clave, aunque alguien registre este objeto por error. */
	@Override
	public String toString() {
		return "JwtProperties[secret=***, accessMinutes=" + accessMinutes + ", refreshDays=" + refreshDays + "]";
	}

}
