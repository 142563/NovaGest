package com.proyecto.tramites.comun.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tarjeta 20: conexion al servicio SOAP de tipo de cambio del Banguat.
 * Por defecto: 5 segundos de espera y dos reintentos (ver docs/investigacion-apis-publicas.md).
 *
 * @param url direccion del servicio TipoCambio.asmx
 * @param tiempoEspera tiempo maximo para conectar y para recibir la respuesta
 * @param reintentos intentos adicionales si el servicio no responde o responde 5xx
 * @param pausaEntreReintentos espera entre un intento y el siguiente
 */
@ConfigurationProperties(prefix = "app.banguat")
public record BanguatProperties(
		@DefaultValue("https://www.banguat.gob.gt/variables/ws/TipoCambio.asmx") String url,
		@DefaultValue("5s") Duration tiempoEspera,
		@DefaultValue("2") int reintentos,
		@DefaultValue("500ms") Duration pausaEntreReintentos) {

	public BanguatProperties {
		if (reintentos < 0) {
			throw new IllegalArgumentException("app.banguat.reintentos no puede ser negativo");
		}
	}

}
