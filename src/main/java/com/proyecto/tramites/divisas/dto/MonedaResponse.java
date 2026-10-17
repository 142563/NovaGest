package com.proyecto.tramites.divisas.dto;

/** Una moneda disponible para proyectar. */
public record MonedaResponse(String codigo, String nombre, int codigoBanguat, String convencion) {

	public static MonedaResponse de(Moneda moneda) {
		return new MonedaResponse(moneda.name(), moneda.nombre(), moneda.codigoBanguat(), moneda.convencion());
	}

}
