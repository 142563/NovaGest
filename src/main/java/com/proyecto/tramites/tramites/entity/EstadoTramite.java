package com.proyecto.tramites.tramites.entity;

/**
 * Los siete estados de un tramite. Los nombres son los mismos de la base de datos.
 * Las transiciones validas (un solo mapa en este enum) llegan con la tarjeta 16.
 */
public enum EstadoTramite {
	BORRADOR, ENVIADO, EN_REVISION, OBSERVADO, POR_PAGAR, FINALIZADO, RECHAZADO;

	/** Finalizado y Rechazado son finales: no admiten mas cambios. */
	public boolean esFinal() {
		return this == FINALIZADO || this == RECHAZADO;
	}

	/** El ciudadano solo edita los datos de su tramite en Borrador y Observado. */
	public boolean ciudadanoPuedeEditar() {
		return this == BORRADOR || this == OBSERVADO;
	}
}
