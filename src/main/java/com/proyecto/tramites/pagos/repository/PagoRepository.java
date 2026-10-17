package com.proyecto.tramites.pagos.repository;

import com.proyecto.tramites.pagos.entity.EstadoPago;
import com.proyecto.tramites.pagos.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PagoRepository extends JpaRepository<Pago, Long>, JpaSpecificationExecutor<Pago> {

	boolean existsByTramiteIdAndEstado(Long tramiteId, EstadoPago estado);

}
