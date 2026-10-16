package com.proyecto.tramites.tramites.repository;

import com.proyecto.tramites.tramites.entity.TipoTramite;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoTramiteRepository extends JpaRepository<TipoTramite, Long> {

	Page<TipoTramite> findByEliminadoEnIsNull(Pageable paginacion);

	Page<TipoTramite> findByEliminadoEnIsNullAndActivo(boolean activo, Pageable paginacion);

	Optional<TipoTramite> findByIdAndEliminadoEnIsNull(Long id);

	boolean existsByNombreIgnoreCaseAndEliminadoEnIsNull(String nombre);

	boolean existsByNombreIgnoreCaseAndEliminadoEnIsNullAndIdNot(String nombre, Long id);

}
