package com.proyecto.tramites.pagos.service;

import java.util.Optional;

/** Formatos de comprobante aceptados. Se reconocen por los primeros bytes del archivo, no por lo que diga el cliente. */
public enum TipoComprobante {

	PDF("application/pdf", "pdf"),
	PNG("image/png", "png"),
	JPG("image/jpeg", "jpg");

	private final String tipoMime;
	private final String extension;

	TipoComprobante(String tipoMime, String extension) {
		this.tipoMime = tipoMime;
		this.extension = extension;
	}

	public String tipoMime() {
		return tipoMime;
	}

	public String extension() {
		return extension;
	}

	/** Reconoce el formato real mirando la cabecera del archivo. Vacio si no es PDF, PNG ni JPG. */
	public static Optional<TipoComprobante> detectar(byte[] contenido) {
		if (empieza(contenido, '%', 'P', 'D', 'F', '-')) {
			return Optional.of(PDF);
		}
		if (empieza(contenido, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
			return Optional.of(PNG);
		}
		if (empieza(contenido, 0xFF, 0xD8, 0xFF)) {
			return Optional.of(JPG);
		}
		return Optional.empty();
	}

	public static Optional<TipoComprobante> dePorMime(String tipoMime) {
		for (TipoComprobante tipo : values()) {
			if (tipo.tipoMime.equals(tipoMime)) {
				return Optional.of(tipo);
			}
		}
		return Optional.empty();
	}

	private static boolean empieza(byte[] contenido, int... esperado) {
		if (contenido == null || contenido.length < esperado.length) {
			return false;
		}
		for (int i = 0; i < esperado.length; i++) {
			if ((contenido[i] & 0xFF) != esperado[i]) {
				return false;
			}
		}
		return true;
	}

}
