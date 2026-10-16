package com.proyecto.tramites.tramites.repository;

import com.proyecto.tramites.tramites.entity.Tramite;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TramiteRepository extends JpaRepository<Tramite, Long> {

	@EntityGraph(attributePaths = "tipoTramite")
	Page<Tramite> findByEliminadoEnIsNull(Pageable paginacion);

	@EntityGraph(attributePaths = "tipoTramite")
	Page<Tramite> findByCiudadanoIdAndEliminadoEnIsNull(Long ciudadanoId, Pageable paginacion);

	@EntityGraph(attributePaths = "tipoTramite")
	Optional<Tramite> findByIdAndEliminadoEnIsNull(Long id);

}
