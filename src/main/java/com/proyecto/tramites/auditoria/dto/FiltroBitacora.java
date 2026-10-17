package com.proyecto.tramites.auditoria.dto;

import com.proyecto.tramites.comun.excepcion.DatosInvalidosException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filtros opcionales de la consulta de bitacora, combinados con Y. desde y hasta son fechas AAAA-MM-DD en hora
 * de Guatemala y ambas se incluyen completas.
 */
public record FiltroBitacora(String recurso, Long recursoId, String accion, Long actorId, LocalDate desde,
		LocalDate hasta) {

	private static final int LARGO_MAXIMO_TEXTO = 50;

	public static FiltroBitacora de(String recurso, Long recursoId, String accion, Long actorId, String desde,
			String hasta) {
		DatosInvalidosException.Acumulador errores = new DatosInvalidosException.Acumulador();
		String recursoLimpio = texto("recurso", recurso, errores);
		String accionLimpia = texto("accion", accion, errores);
		LocalDate fechaDesde = fecha("desde", desde, errores);
		LocalDate fechaHasta = fecha("hasta", hasta, errores);
		if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
			errores.agregar("desde", "No puede ser posterior a hasta");
		}
		errores.lanzarSiHayErrores();
		return new FiltroBitacora(recursoLimpio, recursoId, accionLimpia, actorId, fechaDesde, fechaHasta);
	}

	/** Resumen de los filtros usados, para dejar constancia de lo que se consulto (sin resultados). */
	public Map<String, Object> resumen() {
		Map<String, Object> resumen = new LinkedHashMap<>();
		agregar(resumen, "recurso", recurso);
		agregar(resumen, "recursoId", recursoId);
		agregar(resumen, "accion", accion);
		agregar(resumen, "actorId", actorId);
		agregar(resumen, "desde", desde);
		agregar(resumen, "hasta", hasta);
		return resumen;
	}

	private static void agregar(Map<String, Object> mapa, String clave, Object valor) {
		if (valor != null) {
			mapa.put(clave, valor.toString());
		}
	}

	private static String texto(String campo, String valor, DatosInvalidosException.Acumulador errores) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.trim();
		if (limpio.length() > LARGO_MAXIMO_TEXTO) {
			errores.agregar(campo, "Maximo " + LARGO_MAXIMO_TEXTO + " caracteres");
			return null;
		}
		return limpio;
	}

	private static LocalDate fecha(String campo, String valor, DatosInvalidosException.Acumulador errores) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(valor.trim());
		}
		catch (DateTimeParseException e) {
			errores.agregar(campo, "Debe ser una fecha valida en formato AAAA-MM-DD");
			return null;
		}
	}

}
