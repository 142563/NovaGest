package com.proyecto.tramites.divisas.service;

import com.proyecto.tramites.auditoria.service.BitacoraService;
import com.proyecto.tramites.divisas.dto.Cotizacion;
import com.proyecto.tramites.divisas.dto.Moneda;
import com.proyecto.tramites.divisas.dto.ResultadoActualizacion;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tarjeta 21: deja guardado el tipo de cambio del dia de todas las monedas disponibles, para que la
 * proyeccion casi nunca tenga que esperar al Banguat. Cada moneda va en su propia transaccion: si una falla,
 * las demas se guardan igual. Al final deja una fila en la bitacora con el resultado (actor del sistema).
 */
@Service
public class ActualizacionTipoCambioService {

	static final String ACCION = "ACTUALIZAR_TIPO_CAMBIO";

	private static final Logger LOG = LoggerFactory.getLogger(ActualizacionTipoCambioService.class);

	private final TipoCambioService tiposDeCambio;
	private final BitacoraService bitacora;
	private final TransactionTemplate transaccion;

	public ActualizacionTipoCambioService(TipoCambioService tiposDeCambio, BitacoraService bitacora,
			PlatformTransactionManager transacciones) {
		this.tiposDeCambio = tiposDeCambio;
		this.bitacora = bitacora;
		this.transaccion = new TransactionTemplate(transacciones);
	}

	/** Actualiza el dia de hoy (hora de Guatemala). */
	public ResultadoActualizacion actualizarHoy() {
		return actualizar(tiposDeCambio.hoy());
	}

	public ResultadoActualizacion actualizar(LocalDate fecha) {
		List<String> actualizadas = new ArrayList<>();
		List<String> estimadas = new ArrayList<>();
		List<String> sinDatos = new ArrayList<>();

		for (Moneda moneda : Moneda.values()) {
			try {
				Cotizacion cotizacion = tiposDeCambio.obtener(moneda, fecha);
				(cotizacion.estimado() ? estimadas : actualizadas).add(moneda.name());
			}
			catch (ResponseStatusException ex) {
				sinDatos.add(moneda.name());
			}
			catch (RuntimeException ex) {
				// Un error inesperado en una moneda no detiene a las demas
				LOG.error("Error al actualizar el tipo de cambio de {}: {}", moneda, ex.getClass().getSimpleName());
				sinDatos.add(moneda.name());
			}
		}

		ResultadoActualizacion resultado = new ResultadoActualizacion(fecha, List.copyOf(actualizadas),
				List.copyOf(estimadas), List.copyOf(sinDatos));
		registrarEnBitacora(resultado);
		if (resultado.completa()) {
			LOG.info("Tipos de cambio del {} actualizados: {} monedas", fecha, actualizadas.size());
		}
		else {
			LOG.warn("Tipos de cambio del {}: {} confirmadas, sin Banguat {} estimadas y {} sin datos", fecha,
					actualizadas.size(), estimadas, sinDatos);
		}
		return resultado;
	}

	private void registrarEnBitacora(ResultadoActualizacion resultado) {
		Map<String, Object> despues = new LinkedHashMap<>();
		despues.put("fecha", resultado.fecha().toString());
		despues.put("actualizadas", resultado.actualizadas());
		despues.put("estimadas", resultado.estimadas());
		despues.put("sinDatos", resultado.sinDatos());
		transaccion.executeWithoutResult(estado -> bitacora.registrar(null, ACCION, "tipo_cambio", null, null, despues));
	}

}
