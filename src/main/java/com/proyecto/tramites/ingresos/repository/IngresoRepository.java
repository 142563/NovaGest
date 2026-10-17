package com.proyecto.tramites.ingresos.repository;

import com.proyecto.tramites.ingresos.entity.Ingreso;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IngresoRepository extends JpaRepository<Ingreso, Long>, JpaSpecificationExecutor<Ingreso> {

	/** Busca un ingreso vivo de ese dueno. Uno ajeno o eliminado no aparece (el servicio responde 404). */
	Optional<Ingreso> findByIdAndUsuarioIdAndEliminadoEnIsNull(Long id, Long usuarioId);

}
