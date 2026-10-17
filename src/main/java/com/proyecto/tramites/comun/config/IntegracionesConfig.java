package com.proyecto.tramites.comun.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Parametros de los servicios externos (tarjeta 20: Banguat). Las URL se pueden cambiar por entorno. */
@Configuration
@EnableConfigurationProperties(BanguatProperties.class)
public class IntegracionesConfig {
}
