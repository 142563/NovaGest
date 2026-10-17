package com.proyecto.tramites.ingresos.service;

import com.proyecto.tramites.comun.excepcion.DatosInvalidosException;
import com.proyecto.tramites.divisas.dto.ConvencionCotizacion;
import com.proyecto.tramites.divisas.dto.Cotizacion;
import com.proyecto.tramites.divisas.dto.Moneda;
import com.proyecto.tramites.divisas.service.TipoCambioService;
import com.proyecto.tramites.ingresos.dto.ProyeccionResponse;
import com.proyecto.tramites.ingresos.entity.Ingreso;
import com.proyecto.tramites.ingresos.repository.IngresoEspecificaciones;
import com.proyecto.tramites.ingresos.repository.IngresoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarjeta 22 (RF-10): suma los ingresos propios y los proyecta a otra moneda. Primero pasa los quetzales a
 * dolares con el tipo de cambio del dolar y luego aplica el valor de la moneda segun como se cotiza (el euro y
 * la libra en dolares por unidad; el resto en unidades por dolar). El tipo de cambio sale de TipoCambioService:
 * si el Banguat no responde, la proyeccion se calcula con el ultimo valor guardado y se marca como estimada.
 */
@Service
public class ProyeccionService {

	private final IngresoRepository ingresos;
	private final TipoCambioService tiposDeCambio;

	public ProyeccionService(IngresoRepository ingresos, TipoCambioService tiposDeCambio) {
		this.ingresos = ingresos;
		this.tiposDeCambio = tiposDeCambio;
	}

	/** Lectura de ingresos mas, si hace falta, guardar el tipo de cambio consultado: por eso no es readOnly. */
	@Transactional
	public ProyeccionResponse proyectar(Long duenoId, String codigoMoneda, String textoDesde, String textoHasta,
			String textoFecha) {
		Moneda moneda = Moneda.requerida(codigoMoneda);
		DatosInvalidosException.Acumulador errores = new DatosInvalidosException.Acumulador();
		LocalDate desde = fecha(textoDesde, "desde", errores);
		LocalDate hasta = fecha(textoHasta, "hasta", errores);
		LocalDate fecha = fecha(textoFecha, "fecha", errores);
		if (desde != null && hasta != null && desde.isAfter(hasta)) {
			errores.agregar("desde", "No puede ser posterior a 'hasta'");
		}
		errores.lanzarSiHayErrores();
		LocalDate dia = tiposDeCambio.fechaDeConsulta(fecha);

		List<Ingreso> propios = ingresos.findAll(IngresoEspecificaciones.delDueno(duenoId, desde, hasta));
		BigDecimal totalGtq = propios.stream().map(Ingreso::getMontoGtq).reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_EVEN);

		Cotizacion dolar = tiposDeCambio.obtener(Moneda.USD, dia);
		BigDecimal total;
		Cotizacion destino = null;
		if (moneda.esDolar()) {
			total = totalGtq.divide(dolar.valor(), 2, RoundingMode.HALF_EVEN);
		}
		else {
			destino = tiposDeCambio.obtener(moneda, dia);
			ConvencionCotizacion convencion = ConvencionCotizacion.de(moneda.codigoBanguat()).orElseThrow();
			total = convencion.convertirDesdeQuetzales(totalGtq, dolar.valor(), destino.valor())
					.setScale(2, RoundingMode.HALF_EVEN);
		}
		boolean estimado = dolar.estimado() || (destino != null && destino.estimado());

		return new ProyeccionResponse(moneda.name(), moneda.nombre(), desde, hasta, propios.size(), totalGtq, total,
				dia, dolar.valor(), dolar.fecha(), destino == null ? null : destino.valor(),
				destino == null ? null : destino.fecha(), moneda.convencion(), dolar.fuente(), estimado);
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

}
