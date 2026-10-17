package com.proyecto.tramites.tramites.dto;

import com.proyecto.tramites.comun.excepcion.DatosInvalidosException;
import com.proyecto.tramites.tramites.entity.EstadoTramite;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tarjeta 17: filtros opcionales del listado de tramites. Todos se combinan con Y.
 *
 * @param estados uno o varios estados (vacio = cualquiera)
 * @param tipoId tipo de tramite (null = cualquiera)
 * @param desde fecha de creacion desde, incluida (null = sin limite)
 * @param hasta fecha de creacion hasta, incluida (null = sin limite)
 */
public record FiltroTramites(Set<EstadoTramite> estados, Long tipoId, LocalDate desde, LocalDate hasta) {

	public FiltroTramites {
		estados = estados == null || estados.isEmpty() ? Set.of() : Set.copyOf(estados);
	}

	public static FiltroTramites sinFiltros() {
		return new FiltroTramites(Set.of(), null, null, null);
	}

	/**
	 * Lee los parametros tal como llegan en la URL. Si alguno no es valido responde 400 con el campo y el
	 * motivo, sin repetir el valor recibido.
	 */
	public static FiltroTramites de(List<String> estados, String tipoId, String desde, String hasta) {
		DatosInvalidosException.Acumulador errores = new DatosInvalidosException.Acumulador();

		Set<EstadoTramite> leidos = EnumSet.noneOf(EstadoTramite.class);
		if (estados != null) {
			for (String texto : estados) {
				for (String parte : texto.split(",")) {
					if (parte.isBlank()) {
						continue;
					}
					try {
						leidos.add(EstadoTramite.valueOf(parte.strip().toUpperCase(Locale.ROOT)));
					}
					catch (IllegalArgumentException ex) {
						errores.agregar("estado", "Estado no valido. Valores permitidos: " + permitidos());
					}
				}
			}
		}

		Long tipo = null;
		if (tipoId != null && !tipoId.isBlank()) {
			try {
				tipo = Long.valueOf(tipoId.strip());
				if (tipo <= 0) {
					errores.agregar("tipoId", "Debe ser un numero positivo");
				}
			}
			catch (NumberFormatException ex) {
				errores.agregar("tipoId", "Debe ser un numero positivo");
			}
		}

		LocalDate inicio = fecha(desde, "desde", errores);
		LocalDate fin = fecha(hasta, "hasta", errores);
		if (inicio != null && fin != null && inicio.isAfter(fin)) {
			errores.agregar("desde", "No puede ser posterior a 'hasta'");
		}

		errores.lanzarSiHayErrores();
		return new FiltroTramites(leidos, tipo, inicio, fin);
	}

	private static LocalDate fecha(String texto, String campo, DatosInvalidosException.Acumulador errores) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(texto.strip());
		}
		catch (DateTimeParseException ex) {
			errores.agregar(campo, "Fecha no valida. Formato: AAAA-MM-DD");
			return null;
		}
	}

	private static String permitidos() {
		return Arrays.stream(EstadoTramite.values()).map(Enum::name).collect(Collectors.joining(", "));
	}

}
