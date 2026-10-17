package com.proyecto.tramites.comun.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tarjeta 5: el permiso que muestra Swagger sale del @PreAuthorize, en texto legible. */
class OpenApiConfigTests {

	@Test
	void dejaSoloElNombreDelPermiso() {
		assertThat(OpenApiConfig.permisoLegible("hasAuthority('tramite:leer')")).isEqualTo("tramite:leer");
	}

	@Test
	void listaVariosPermisosSiLaReglaLosCombina() {
		assertThat(OpenApiConfig.permisoLegible("hasAuthority('tramite:leer') or hasAuthority('tramite:revisar')"))
				.isEqualTo("tramite:leer, tramite:revisar");
	}

	@Test
	void unaReglaDistintaSeMuestraTalCual() {
		assertThat(OpenApiConfig.permisoLegible("isAuthenticated()")).isEqualTo("isAuthenticated()");
	}

}
