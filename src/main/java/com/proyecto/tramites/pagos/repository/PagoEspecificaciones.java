package com.proyecto.tramites.pagos.repository;

import com.proyecto.tramites.pagos.entity.EstadoPago;
import com.proyecto.tramites.pagos.entity.Pago;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Arma la consulta del listado con los filtros que vengan; los valores siempre van como parametros. */
public final class PagoEspecificaciones {

	private PagoEspecificaciones() {
	}

	/**
	 * @param ciudadanoId dueno de los tramites; null si quien consulta ve los pagos de todos
	 */
	public static Specification<Pago> listado(Long ciudadanoId, EstadoPago estado, Long tramiteId) {
		return (raiz, consulta, cb) -> {
			List<Predicate> condiciones = new ArrayList<>();
			if (ciudadanoId != null) {
				condiciones.add(cb.equal(raiz.get("ciudadanoId"), ciudadanoId));
			}
			if (estado != null) {
				condiciones.add(cb.equal(raiz.get("estado"), estado));
			}
			if (tramiteId != null) {
				condiciones.add(cb.equal(raiz.get("tramiteId"), tramiteId));
			}
			return cb.and(condiciones.toArray(Predicate[]::new));
		};
	}

}
