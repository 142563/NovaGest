package com.proyecto.tramites.auditoria.repository;

import com.proyecto.tramites.auditoria.dto.FiltroBitacora;
import com.proyecto.tramites.auditoria.entity.Bitacora;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Arma la consulta de la bitacora con los filtros que vengan; los valores siempre van como parametros. */
public final class BitacoraEspecificaciones {

	public static final ZoneId ZONA = ZoneId.of("America/Guatemala");

	private BitacoraEspecificaciones() {
	}

	public static Specification<Bitacora> listado(FiltroBitacora filtro) {
		return (raiz, consulta, cb) -> {
			List<Predicate> condiciones = new ArrayList<>();
			if (filtro.recurso() != null) {
				condiciones.add(cb.equal(raiz.get("recurso"), filtro.recurso()));
			}
			if (filtro.recursoId() != null) {
				condiciones.add(cb.equal(raiz.get("recursoId"), filtro.recursoId()));
			}
			if (filtro.accion() != null) {
				condiciones.add(cb.equal(raiz.get("accion"), filtro.accion()));
			}
			if (filtro.actorId() != null) {
				condiciones.add(cb.equal(raiz.get("actorId"), filtro.actorId()));
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
