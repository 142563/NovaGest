package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Detecta antes de arrancar la aplicacion que dos migraciones de Flyway usen la misma version. Pasa cuando dos
 * personas agregan una migracion en ramas distintas: las dos eligen el siguiente numero y, al juntarlas, la
 * aplicacion no arranca. No necesita base de datos ni Docker.
 */
class MigracionesTests {

	private static final Pattern NOMBRE = Pattern.compile("^V(\\d+)__[a-z0-9_]+\\.sql$");

	private static List<String> nombres() throws IOException {
		List<String> nombres = new ArrayList<>();
		for (Resource recurso : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
			nombres.add(recurso.getFilename());
		}
		return nombres;
	}

	@Test
	void ningunaVersionDeMigracionSeRepite() throws IOException {
		Map<Integer, List<String>> porVersion = new TreeMap<>();
		for (String nombre : nombres()) {
			Matcher m = NOMBRE.matcher(nombre);
			assertThat(m.matches()).as("Nombre de migracion invalido: " + nombre + " (formato V<n>__descripcion.sql)").isTrue();
			porVersion.computeIfAbsent(Integer.valueOf(m.group(1)), k -> new ArrayList<>()).add(nombre);
		}

		Map<Integer, List<String>> repetidas = new TreeMap<>();
		porVersion.forEach((version, archivos) -> {
			if (archivos.size() > 1) {
				repetidas.put(version, archivos);
			}
		});
		assertThat(repetidas)
				.as("Versiones de migracion repetidas: renumera la tuya a la siguiente libre antes de mezclar")
				.isEmpty();
	}

	@Test
	void existeLaMigracionBase() throws IOException {
		assertThat(nombres()).contains("V1__linea_base.sql");
	}

}
