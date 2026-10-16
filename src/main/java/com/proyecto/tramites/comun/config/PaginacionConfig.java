package com.proyecto.tramites.comun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * Listados paginados de toda la API (parametros page, size y sort).
 * El JSON sale estable: content y page (size, number, totalElements, totalPages).
 * Maximo 100 elementos por pagina; si se pide mas, se recorta a 100.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class PaginacionConfig {

	private static final int TAMANO_MAXIMO = 100;

	@Bean
	PageableHandlerMethodArgumentResolverCustomizer limitesDePaginacion() {
		return resolvedor -> resolvedor.setMaxPageSize(TAMANO_MAXIMO);
	}

}
