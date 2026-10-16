package com.proyecto.tramites.tramites.service;

import com.proyecto.tramites.comun.util.Paginacion;
import com.proyecto.tramites.tramites.dto.TipoTramiteRequest;
import com.proyecto.tramites.tramites.dto.TipoTramiteResponse;
import com.proyecto.tramites.tramites.entity.TipoTramite;
import com.proyecto.tramites.tramites.repository.TipoTramiteRepository;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reglas de los tipos de tramite. Quien puede editar (tipo_tramite:editar) ve tambien los inactivos;
 * los demas solo ven los activos y piden uno inactivo como si no existiera (404).
 */
@Service
public class TipoTramiteService {

	private static final Set<String> ORDENABLES = Set.of("nombre", "monto", "creadoEn");

	private final TipoTramiteRepository tipos;

	public TipoTramiteService(TipoTramiteRepository tipos) {
		this.tipos = tipos;
	}

	@Transactional(readOnly = true)
	public Page<TipoTramiteResponse> listar(boolean administra, Boolean activo, Pageable paginacion) {
		Pageable pagina = Paginacion.validarOrden(paginacion, ORDENABLES);
		Boolean filtro = administra ? activo : Boolean.TRUE;
		Page<TipoTramite> resultado = filtro == null
				? tipos.findByEliminadoEnIsNull(pagina)
				: tipos.findByEliminadoEnIsNullAndActivo(filtro, pagina);
		return resultado.map(TipoTramiteResponse::de);
	}

	@Transactional(readOnly = true)
	public TipoTramiteResponse obtener(Long id, boolean administra) {
		return TipoTramiteResponse.de(buscar(id, administra));
	}

	@Transactional
	public TipoTramiteResponse crear(TipoTramiteRequest solicitud) {
		String nombre = solicitud.nombre().trim();
		if (tipos.existsByNombreIgnoreCaseAndEliminadoEnIsNull(nombre)) {
			throw nombreEnUso();
		}
		TipoTramite tipo = new TipoTramite(nombre, limpiar(solicitud.descripcion()), solicitud.monto(),
				solicitud.activoONulo());
		return TipoTramiteResponse.de(tipos.saveAndFlush(tipo));
	}

	@Transactional
	public TipoTramiteResponse actualizar(Long id, TipoTramiteRequest solicitud) {
		TipoTramite tipo = buscar(id, true);
		String nombre = solicitud.nombre().trim();
		if (tipos.existsByNombreIgnoreCaseAndEliminadoEnIsNullAndIdNot(nombre, id)) {
			throw nombreEnUso();
		}
		tipo.actualizar(nombre, limpiar(solicitud.descripcion()), solicitud.monto(), solicitud.activoONulo());
		return TipoTramiteResponse.de(tipos.saveAndFlush(tipo));
	}

	/** Eliminacion logica: el registro se marca y queda en la base. */
	@Transactional
	public void eliminar(Long id) {
		TipoTramite tipo = buscar(id, true);
		tipo.eliminarLogicamente();
		tipos.saveAndFlush(tipo);
	}

	private TipoTramite buscar(Long id, boolean administra) {
		return tipos.findByIdAndEliminadoEnIsNull(id)
				.filter(tipo -> administra || tipo.isActivo())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El tipo de tramite no existe"));
	}

	private static String limpiar(String texto) {
		return texto == null || texto.isBlank() ? null : texto.trim();
	}

	private static ResponseStatusException nombreEnUso() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un tipo de tramite con ese nombre");
	}

}
