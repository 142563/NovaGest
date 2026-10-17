package com.proyecto.tramites.comun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Donde se guardan los comprobantes de pago: una carpeta del servidor (variable COMPROBANTES_DIR).
 * En Docker es un volumen, para que los archivos sobrevivan al contenedor.
 */
@ConfigurationProperties(prefix = "app.pagos")
public record PagosProperties(String directorio) {

}
