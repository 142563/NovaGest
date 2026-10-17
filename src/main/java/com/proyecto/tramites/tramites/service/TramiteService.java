package com.proyecto.tramites.tramites.service;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	public TramiteService(TramiteRepository tramites, TipoTramiteRepository tipos, ValidadorDatosTramite validador,
			TramiteHistorialRepository historial) {
		this.tramites = tramites;
		this.tipos = tipos;
		this.validador = validador;
		this.historial = historial;
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
		Map<String, Object> datos = validador.validar(tramite.getTipoTramite(), solicitud.datos());
		tramite.reemplazarDatos(validador.aJson(datos));
		tramites.saveAndFlush(tramite);
		return TramiteResponse.de(tramite, datos);
	}

	/** Eliminacion logica: el tramite se marca y queda en la base. */
	@Transactional
	public void eliminar(Actor actor, Long id) {
		Tramite tramite = buscarVisible(actor, id);
		tramite.eliminarLogicamente();
		tramites.saveAndFlush(tramite);
	}

	private Tramite buscarVisible(Actor actor, Long id) {
		return tramites.findByIdAndEliminadoEnIsNull(id)
				.filter(tramite -> actor.alcanceTotal() || tramite.getCiudadanoId().equals(actor.id()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El tramite no existe"));
	}

}
