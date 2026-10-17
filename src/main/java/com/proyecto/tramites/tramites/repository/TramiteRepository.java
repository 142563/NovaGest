package com.proyecto.tramites.tramites.repository;

import com.proyecto.tramites.tramites.entity.Tramite;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TramiteRepository extends JpaRepository<Tramite, Long>, JpaSpecificationExecutor<Tramite> {

	@EntityGraph(attributePaths = "tipoTramite")
	Page<Tramite> findByEliminadoEnIsNull(Pageable paginacion);

	@EntityGraph(attributePaths = "tipoTramite")
	Page<Tramite> findByCiudadanoIdAndEliminadoEnIsNull(Long ciudadanoId, Pageable paginacion);

	@EntityGraph(attributePaths = "tipoTramite")
	Optional<Tramite> findByIdAndEliminadoEnIsNull(Long id);

	/**
	 * Bloquea la fila mientras dura la transaccion: si dos personas piden una transicion al mismo tiempo,
	 * la segunda espera y ve el estado ya cambiado (y responde 409 si ya no es valida).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Tramite t where t.id = :id and t.eliminadoEn is null")
	Optional<Tramite> bloquearPorId(@Param("id") Long id);

	/** Tarjeta 17: listado con filtros (ver TramiteEspecificaciones). Trae el tipo en la misma consulta. */
	@Override
	@EntityGraph(attributePaths = "tipoTramite")
	Page<Tramite> findAll(Specification<Tramite> especificacion, Pageable paginacion);

}
