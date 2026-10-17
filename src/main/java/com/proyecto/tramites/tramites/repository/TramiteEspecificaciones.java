package com.proyecto.tramites.tramites.repository;

import com.proyecto.tramites.tramites.dto.FiltroTramites;
import com.proyecto.tramites.tramites.entity.Tramite;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Tarjeta 17: arma la consulta del listado con los filtros que vengan. Siempre excluye los eliminados y,
 * si el actor no tiene alcance total, se limita a los tramites del ciudadano.
 * Las fechas se interpretan en hora de Guatemala: "hasta" incluye todo ese dia.
 */
public final class TramiteEspecificaciones {

	public static final ZoneId ZONA = ZoneId.of("America/Guatemala");

	private TramiteEspecificaciones() {
	}

	/**
	 * @param ciudadanoId dueno de los tramites; null si el actor ve todos
	 */
	public static Specification<Tramite> listado(Long ciudadanoId, FiltroTramites filtro) {
		return (raiz, consulta, cb) -> {
			List<Predicate> condiciones = new ArrayList<>();
			condiciones.add(cb.isNull(raiz.get("eliminadoEn")));
			if (ciudadanoId != null) {
				condiciones.add(cb.equal(raiz.get("ciudadanoId"), ciudadanoId));
			}
			if (!filtro.estados().isEmpty()) {
				condiciones.add(raiz.get("estado").in(filtro.estados()));
			}
			if (filtro.tipoId() != null) {
				condiciones.add(cb.equal(raiz.get("tipoTramite").get("id"), filtro.tipoId()));
			}
			if (filtro.desde() != null) {
				condiciones.add(cb.greaterThanOrEqualTo(raiz.<Instant>get("creadoEn"),
						filtro.desde().atStartOfDay(ZONA).toInstant()));
			}
			if (filtro.hasta() != null) {
				condiciones.add(cb.lessThan(raiz.<Instant>get("creadoEn"),
						filtro.hasta().plusDays(1).atStartOfDay(ZONA).toInstant()));
			}
			return cb.and(condiciones.toArray(Predicate[]::new));
		};
	}

}
