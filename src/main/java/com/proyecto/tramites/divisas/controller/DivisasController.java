package com.proyecto.tramites.divisas.controller;

import com.proyecto.tramites.comun.excepcion.DatosInvalidosException;
import com.proyecto.tramites.divisas.dto.Cotizacion;
import com.proyecto.tramites.divisas.dto.Moneda;
import com.proyecto.tramites.divisas.dto.MonedaResponse;
import com.proyecto.tramites.divisas.dto.TipoCambioResponse;
import com.proyecto.tramites.divisas.service.TipoCambioService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarjeta 22: monedas disponibles y tipo de cambio. Sirven para proyectar ingresos, por eso exigen
 * ingreso:leer. El controlador solo recibe, valida y responde.
 */
@RestController
@RequestMapping("/api/divisas")
public class DivisasController {

	private final TipoCambioService servicio;

	public DivisasController(TipoCambioService servicio) {
		this.servicio = servicio;
	}

	/** Permiso: ingreso:leer. Monedas a las que se puede proyectar. */
	@GetMapping("/monedas")
	@PreAuthorize("hasAuthority('ingreso:leer')")
	public List<MonedaResponse> monedas() {
		return Arrays.stream(Moneda.values()).map(MonedaResponse::de).toList();
	}

	/**
	 * Permiso: ingreso:leer. Tipo de cambio de una moneda (codigo ISO, por ejemplo EUR) para una fecha
	 * (AAAA-MM-DD, por defecto hoy). Si el Banguat no responde, devuelve el ultimo valor marcado como estimado.
	 */
	@GetMapping("/tipo-cambio")
	@PreAuthorize("hasAuthority('ingreso:leer')")
	public TipoCambioResponse tipoCambio(@RequestParam(required = false) String moneda,
			@RequestParam(required = false) String fecha) {
		Moneda elegida = Moneda.requerida(moneda);
		LocalDate dia = servicio.fechaDeConsulta(leerFecha(fecha));
		Cotizacion cotizacion = servicio.obtener(elegida, dia);
		return new TipoCambioResponse(elegida.name(), dia, cotizacion.fecha(), cotizacion.valor(),
				elegida.convencion(), cotizacion.fuente(), cotizacion.estimado());
	}

	/** Lee una fecha AAAA-MM-DD opcional; si no es valida, 400 sin repetir el valor recibido. */
	private static LocalDate leerFecha(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(texto.strip());
		}
		catch (DateTimeParseException ex) {
			throw DatosInvalidosException.de("fecha", "Fecha no valida. Formato: AAAA-MM-DD");
		}
	}

}
