package com.proyecto.tramites.comun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bloqueo por intentos fallidos: tras maxIntentos fallos seguidos la cuenta se bloquea bloqueoMinutos minutos.
 * El plan propone 5 fallos y 15 minutos.
 */
@ConfigurationProperties(prefix = "app.login")
public record LoginProperties(int maxIntentos, int bloqueoMinutos) {

}
