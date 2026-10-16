package com.proyecto.tramites.comun.excepcion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Los datos enviados no cumplen las reglas del negocio (por ejemplo, un campo que el tipo de tramite
 * no declara). Responde 400 con la lista de campos y motivos, sin el valor rechazado.
 */
public class DatosInvalidosException extends RuntimeException {

	private final List<Map<String, String>> errores;

	public DatosInvalidosException(List<Map<String, String>> errores) {
		super("Datos invalidos en " + errores.size() + " campo(s)");
		this.errores = List.copyOf(errores);
	}

	public static DatosInvalidosException de(String campo, String motivo) {
		return new DatosInvalidosException(List.of(Map.of("campo", campo, "motivo", motivo)));
	}

	public List<Map<String, String>> getErrores() {
		return errores;
	}

	/** Acumula errores de varios campos antes de lanzar una sola excepcion. */
	public static final class Acumulador {

		private final List<Map<String, String>> errores = new ArrayList<>();

		public void agregar(String campo, String motivo) {
			errores.add(Map.of("campo", campo, "motivo", motivo));
		}

		public boolean hayErrores() {
			return !errores.isEmpty();
		}

		public void lanzarSiHayErrores() {
			if (hayErrores()) {
				throw new DatosInvalidosException(errores);
			}
		}

	}

}
