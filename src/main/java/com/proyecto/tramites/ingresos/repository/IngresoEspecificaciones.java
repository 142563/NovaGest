package com.proyecto.tramites.ingresos.repository;

import com.proyecto.tramites.ingresos.entity.Ingreso;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Tarjeta 19: listado de ingresos de un dueno, opcionalmente entre dos fechas (ambas incluidas). */
public final class IngresoEspecificaciones {

	private IngresoEspecificaciones() {
	}

	public static Specification<Ingreso> delDueno(Long usuarioId, LocalDate desde, LocalDate hasta) {
		return (raiz, consulta, cb) -> {
			List<Predicate> condiciones = new ArrayList<>();
			condiciones.add(cb.equal(raiz.get("usuarioId"), usuarioId));
			condiciones.add(cb.isNull(raiz.get("eliminadoEn")));
			if (desde != null) {
				condiciones.add(cb.greaterThanOrEqualTo(raiz.<LocalDate>get("fecha"), desde));
			}
			if (hasta != null) {
				condiciones.add(cb.lessThanOrEqualTo(raiz.<LocalDate>get("fecha"), hasta));
			}
			return cb.and(condiciones.toArray(Predicate[]::new));
		};
	}

}
