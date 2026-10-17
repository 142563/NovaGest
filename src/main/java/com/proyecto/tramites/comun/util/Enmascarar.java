package com.proyecto.tramites.comun.util;

/** Oculta parte de los datos personales cuando una consulta de auditoria los muestra. */
public final class Enmascarar {

	private Enmascarar() {
	}

	/** De ana@demo.com deja a***@demo.com: se reconoce el dominio, pero no la persona completa. */
	public static String correo(String correo) {
		if (correo == null || correo.isBlank()) {
			return null;
		}
		int arroba = correo.indexOf('@');
		if (arroba <= 0) {
			return "***";
		}
		return correo.charAt(0) + "***" + correo.substring(arroba);
	}

}
