package com.proyecto.tramites.tramites.service;

/**
 * Pregunta al modulo de pagos si un tramite ya tiene un pago validado. La transicion Por pagar a
 * Finalizado la exige. El modulo de pagos (tarjeta 18) reemplaza la implementacion por defecto.
 */
public interface ConsultaPagos {

	boolean tienePagoValidado(Long tramiteId);

}
