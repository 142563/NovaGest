package com.proyecto.tramites.divisas.repository;

import com.proyecto.tramites.divisas.entity.TipoCambio;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoCambioRepository extends JpaRepository<TipoCambio, Long> {

	boolean existsByFechaAndMoneda(LocalDate fecha, int moneda);

	/** El valor de esa fecha o, si no hay, el ultimo anterior (fines de semana, feriados o Banguat caido). */
	Optional<TipoCambio> findFirstByMonedaAndFechaLessThanEqualOrderByFechaDesc(int moneda, LocalDate fecha);

}
