package com.proyecto.tramites.divisas.service;

/**
 * El Banguat no respondio a tiempo (tras los reintentos) o devolvio algo que no se pudo interpretar.
 * Quien llama decide si usa el ultimo valor guardado marcado como estimado.
 */
public class BanguatException extends RuntimeException {

	private final boolean reintentable;

	BanguatException(String mensaje, boolean reintentable, Throwable causa) {
		super(mensaje, causa);
		this.reintentable = reintentable;
	}

	/** Verdadero si fue un problema de red, de tiempo o un 5xx; falso si la respuesta no era valida. */
	public boolean esReintentable() {
		return reintentable;
	}

}
