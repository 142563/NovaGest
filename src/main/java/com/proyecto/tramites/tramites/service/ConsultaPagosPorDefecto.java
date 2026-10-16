package com.proyecto.tramites.tramites.service;

import org.springframework.stereotype.Component;

/**
 * Mientras no exista el modulo de pagos (tarjeta 18), ningun tramite tiene un pago validado, asi que nadie
 * puede finalizar un tramite sin pago. Al crear el modulo de pagos se elimina esta clase.
 */
@Component
class ConsultaPagosPorDefecto implements ConsultaPagos {

	@Override
	public boolean tienePagoValidado(Long tramiteId) {
		return false;
	}

}
