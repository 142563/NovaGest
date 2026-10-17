package com.proyecto.tramites.ingresos.service;

import com.proyecto.tramites.auditoria.service.BitacoraService;
import com.proyecto.tramites.comun.excepcion.DatosInvalidosException;
import com.proyecto.tramites.comun.util.Paginacion;
import com.proyecto.tramites.ingresos.dto.IngresoRequest;
import com.proyecto.tramites.ingresos.dto.IngresoResponse;
import com.proyecto.tramites.ingresos.entity.Ingreso;
import com.proyecto.tramites.ingresos.repository.IngresoEspecificaciones;
import com.proyecto.tramites.ingresos.repository.IngresoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tarjeta 19: ingresos propios. Son dato personal, asi que el alcance es siempre el propio, aunque el rol
 * tenga alcance sobre todos los registros: nadie ve ni toca los ingresos de otra persona, y pedir uno ajeno
 * responde 404 para no revelar que existe.
 * <p>
 * Cada creacion, edicion y eliminacion queda en la bitacora en la misma transaccion. Por ser dato personal
 * (hallazgo 6 del plan: el auditor lee la bitacora), alli se guarda la fecha y que campos cambiaron, pero
 * nunca el monto ni la descripcion.
 */
@Service
public class IngresoService {

	static final String RECURSO = "ingreso";

	private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
	private static final Set<String> ORDENABLES = Set.of("id", "fecha", "montoGtq", "creadoEn");

	private final IngresoRepository ingresos;
	private final BitacoraService bitacora;
	private final Clock reloj;

	public IngresoService(IngresoRepository ingresos, BitacoraService bitacora, Clock reloj) {
		this.ingresos = ingresos;
		this.bitacora = bitacora;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public Page<IngresoResponse> listar(Long duenoId, String desde, String hasta, Pageable paginacion) {
		Pageable pagina = Paginacion.validarOrden(paginacion, ORDENABLES);
		DatosInvalidosException.Acumulador errores = new DatosInvalidosException.Acumulador();
		LocalDate inicio = fecha(desde, "desde", errores);
		LocalDate fin = fecha(hasta, "hasta", errores);
		if (inicio != null && fin != null && inicio.isAfter(fin)) {
			errores.agregar("desde", "No puede ser posterior a 'hasta'");
		}
		errores.lanzarSiHayErrores();
		return ingresos.findAll(IngresoEspecificaciones.delDueno(duenoId, inicio, fin), pagina)
				.map(IngresoResponse::de);
	}

	@Transactional(readOnly = true)
	public IngresoResponse obtener(Long duenoId, Long id) {
		return IngresoResponse.de(buscarPropio(duenoId, id));
	}

	@Transactional
	public IngresoResponse crear(Long duenoId, IngresoRequest solicitud) {
		validarFecha(solicitud.fecha());
		Ingreso ingreso = ingresos.saveAndFlush(
				new Ingreso(duenoId, solicitud.fecha(), solicitud.montoGtq(), limpiar(solicitud.descripcion())));
		bitacora.registrar(duenoId, "CREAR_INGRESO", RECURSO, ingreso.getId(), null,
				Map.of("fecha", ingreso.getFecha().toString()));
		return IngresoResponse.de(ingreso);
	}

	@Transactional
	public IngresoResponse actualizar(Long duenoId, Long id, IngresoRequest solicitud) {
		Ingreso ingreso = buscarPropio(duenoId, id);
		validarFecha(solicitud.fecha());
		String descripcion = limpiar(solicitud.descripcion());

		List<String> cambiados = new ArrayList<>();
		if (!ingreso.getFecha().equals(solicitud.fecha())) {
			cambiados.add("fecha");
		}
		if (ingreso.getMontoGtq().compareTo(solicitud.montoGtq()) != 0) {
			cambiados.add("montoGtq");
		}
		if (!Objects.equals(ingreso.getDescripcion(), descripcion)) {
			cambiados.add("descripcion");
		}
		LocalDate fechaAnterior = ingreso.getFecha();

		ingreso.reemplazar(solicitud.fecha(), solicitud.montoGtq(), descripcion);
		ingresos.saveAndFlush(ingreso);

		Map<String, Object> despues = new LinkedHashMap<>();
		despues.put("fecha", ingreso.getFecha().toString());
		despues.put("camposCambiados", cambiados);
		bitacora.registrar(duenoId, "EDITAR_INGRESO", RECURSO, id, Map.of("fecha", fechaAnterior.toString()),
				despues);
		return IngresoResponse.de(ingreso);
	}

	/** Eliminacion logica: el ingreso se marca y queda en la base. */
	@Transactional
	public void eliminar(Long duenoId, Long id) {
		Ingreso ingreso = buscarPropio(duenoId, id);
		ingreso.eliminarLogicamente();
		ingresos.saveAndFlush(ingreso);
		bitacora.registrar(duenoId, "ELIMINAR_INGRESO", RECURSO, id, Map.of("fecha", ingreso.getFecha().toString()),
				Map.of("eliminado", true));
	}

	private Ingreso buscarPropio(Long duenoId, Long id) {
		return ingresos.findByIdAndUsuarioIdAndEliminadoEnIsNull(id, duenoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El ingreso no existe"));
	}

	/** Un ingreso no puede tener fecha futura (hoy se cuenta en hora de Guatemala). */
	private void validarFecha(LocalDate fecha) {
		if (fecha.isAfter(LocalDate.now(reloj.withZone(GUATEMALA)))) {
			throw DatosInvalidosException.de("fecha", "No puede ser una fecha futura");
		}
	}

	private static String limpiar(String texto) {
		return texto == null || texto.isBlank() ? null : texto.strip();
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
