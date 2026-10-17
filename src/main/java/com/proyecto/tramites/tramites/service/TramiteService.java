package com.proyecto.tramites.tramites.service;

import com.proyecto.tramites.auditoria.service.BitacoraService;
import com.proyecto.tramites.comun.excepcion.DatosInvalidosException;
import com.proyecto.tramites.comun.util.Actor;
import com.proyecto.tramites.comun.util.Paginacion;
import com.proyecto.tramites.tramites.dto.HistorialResponse;
import com.proyecto.tramites.tramites.dto.FiltroTramites;
import com.proyecto.tramites.tramites.dto.TramiteActualizarRequest;
import com.proyecto.tramites.tramites.dto.TramiteRequest;
import com.proyecto.tramites.tramites.dto.TramiteResponse;
import com.proyecto.tramites.tramites.entity.TipoTramite;
import com.proyecto.tramites.tramites.entity.Tramite;
import com.proyecto.tramites.tramites.entity.TramiteHistorial;
import com.proyecto.tramites.tramites.repository.TipoTramiteRepository;
import com.proyecto.tramites.tramites.repository.TramiteHistorialRepository;
import com.proyecto.tramites.tramites.repository.TramiteEspecificaciones;
import com.proyecto.tramites.tramites.repository.TramiteRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reglas de los tramites. Dos niveles de control: el permiso de la accion (en el controlador) y la
 * propiedad del dato (aqui): quien no tiene alcance sobre todos solo ve y toca lo propio, y pedir un
 * tramite ajeno responde 404 para no revelar que existe.
 */
@Service
public class TramiteService {

	private static final Set<String> ORDENABLES = Set.of("id", "creadoEn", "estado");

	private final TramiteRepository tramites;
	private final TipoTramiteRepository tipos;
	private final ValidadorDatosTramite validador;
	private final TramiteHistorialRepository historial;
	private final BitacoraService bitacora;

	public TramiteService(TramiteRepository tramites, TipoTramiteRepository tipos, ValidadorDatosTramite validador,
			TramiteHistorialRepository historial, BitacoraService bitacora) {
		this.tramites = tramites;
		this.tipos = tipos;
		this.validador = validador;
		this.historial = historial;
		this.bitacora = bitacora;
	}

	/** Abre un tramite en Borrador a nombre de quien lo pide. Los datos pueden estar incompletos. */
	@Transactional
	public TramiteResponse crear(Actor actor, TramiteRequest solicitud) {
		TipoTramite tipo = tipos.findByIdAndEliminadoEnIsNull(solicitud.tipoId())
				.filter(TipoTramite::isActivo)
				.orElseThrow(() -> DatosInvalidosException.de("tipoId", "El tipo de tramite no existe o no esta activo"));
		Map<String, Object> datos = validador.validar(tipo, solicitud.datos());
		Tramite tramite = tramites.saveAndFlush(new Tramite(tipo, actor.id(), validador.aJson(datos)));
		// La primera fila del historial es la creacion: no hay estado anterior
		historial.save(new TramiteHistorial(tramite.getId(), null, tramite.getEstado(), actor.id(), null));
		bitacora.registrar(actor.id(), "CREAR_TRAMITE", "tramite", tramite.getId(), null,
				Map.of("tipoId", tipo.getId(), "estado", tramite.getEstado().name()));
		return TramiteResponse.de(tramite, datos);
	}

	/** Historial de cambios de estado, del mas antiguo al mas reciente. Mismas reglas de visibilidad que el tramite. */
	@Transactional(readOnly = true)
	public List<HistorialResponse> historial(Actor actor, Long id) {
		buscarVisible(actor, id);
		return historial.findByTramiteIdOrderByIdAsc(id).stream().map(HistorialResponse::de).toList();
	}

	@Transactional(readOnly = true)
	public Page<TramiteResponse> listar(Actor actor, Pageable paginacion) {
		return listar(actor, FiltroTramites.sinFiltros(), paginacion);
	}

	/**
	 * Tarjeta 17: listado con filtros por estado, tipo y fechas de creacion. El alcance se aplica igual:
	 * un ciudadano filtra solo dentro de sus propios tramites.
	 */
	@Transactional(readOnly = true)
	public Page<TramiteResponse> listar(Actor actor, FiltroTramites filtro, Pageable paginacion) {
		Pageable pagina = Paginacion.validarOrden(paginacion, ORDENABLES);
		Long dueno = actor.alcanceTotal() ? null : actor.id();
		Page<Tramite> resultado = tramites.findAll(TramiteEspecificaciones.listado(dueno, filtro), pagina);
		return resultado.map(tramite -> TramiteResponse.de(tramite, validador.deJson(tramite.getDatos())));
	}

	@Transactional(readOnly = true)
	public TramiteResponse obtener(Actor actor, Long id) {
		Tramite tramite = buscarVisible(actor, id);
		return TramiteResponse.de(tramite, validador.deJson(tramite.getDatos()));
	}

	/**
	 * Reemplaza los datos del tramite. El ciudadano solo puede en Borrador u Observado; quien tiene alcance
	 * sobre todos, en cualquier estado salvo los finales. El estado no se toca aqui.
	 */
	@Transactional
	public TramiteResponse actualizar(Actor actor, Long id, TramiteActualizarRequest solicitud) {
		Tramite tramite = buscarVisible(actor, id);
		if (tramite.getEstado().esFinal()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"El tramite esta en un estado final y no admite cambios");
		}
		if (!actor.alcanceTotal() && !tramite.getEstado().ciudadanoPuedeEditar()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Solo se pueden editar los datos en estado BORRADOR u OBSERVADO");
		}
		Map<String, Object> anteriores = validador.deJson(tramite.getDatos());
		Map<String, Object> datos = validador.validar(tramite.getTipoTramite(), solicitud.datos());
		tramite.reemplazarDatos(validador.aJson(datos));
		tramites.saveAndFlush(tramite);
		// En la bitacora solo van los nombres de los campos que cambiaron, nunca sus valores (son datos personales)
		bitacora.registrar(actor.id(), "EDITAR_TRAMITE", "tramite", tramite.getId(),
				Map.of("estado", tramite.getEstado().name()),
				Map.of("camposModificados", camposModificados(anteriores, datos)));
		return TramiteResponse.de(tramite, datos);
	}

	/** Eliminacion logica: el tramite se marca y queda en la base. */
	@Transactional
	public void eliminar(Actor actor, Long id) {
		Tramite tramite = buscarVisible(actor, id);
		String estado = tramite.getEstado().name();
		tramite.eliminarLogicamente();
		tramites.saveAndFlush(tramite);
		bitacora.registrar(actor.id(), "ELIMINAR_TRAMITE", "tramite", tramite.getId(), Map.of("estado", estado),
				Map.of("eliminado", true));
	}

	/** Claves cuyo valor cambio, se agrego o se quito. Los numeros se comparan por valor (2500 igual a 2500.00). */
	private static List<String> camposModificados(Map<String, Object> antes, Map<String, Object> despues) {
		Set<String> claves = new TreeSet<>(antes.keySet());
		claves.addAll(despues.keySet());
		return claves.stream().filter(clave -> !iguales(antes.get(clave), despues.get(clave))).toList();
	}

	private static boolean iguales(Object a, Object b) {
		if (a instanceof Number x && b instanceof Number y) {
			return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
		}
		return Objects.equals(a, b);
	}

	private Tramite buscarVisible(Actor actor, Long id) {
		return tramites.findByIdAndEliminadoEnIsNull(id)
				.filter(tramite -> actor.alcanceTotal() || tramite.getCiudadanoId().equals(actor.id()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El tramite no existe"));
	}

}
