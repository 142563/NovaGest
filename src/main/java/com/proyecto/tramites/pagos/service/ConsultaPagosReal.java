package com.proyecto.tramites.pagos.service;

import com.proyecto.tramites.pagos.entity.EstadoPago;
import com.proyecto.tramites.pagos.repository.PagoRepository;
import com.proyecto.tramites.tramites.service.ConsultaPagos;
import org.springframework.stereotype.Component;

/** Responde al modulo de tramites si un tramite ya tiene un pago validado (condicion de Por pagar a Finalizado). */
@Component
class ConsultaPagosReal implements ConsultaPagos {

	private final PagoRepository pagos;

	ConsultaPagosReal(PagoRepository pagos) {
		this.pagos = pagos;
	}

	@Override
	public boolean tienePagoValidado(Long tramiteId) {
		return pagos.existsByTramiteIdAndEstado(tramiteId, EstadoPago.VALIDADO);
	}

}
