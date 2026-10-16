package com.proyecto.tramites.tramites.repository;

import com.proyecto.tramites.tramites.entity.TramiteHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TramiteHistorialRepository extends JpaRepository<TramiteHistorial, Long> {

	List<TramiteHistorial> findByTramiteIdOrderByIdAsc(Long tramiteId);

}
