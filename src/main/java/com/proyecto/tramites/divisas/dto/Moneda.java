package com.proyecto.tramites.divisas.dto;

import com.proyecto.tramites.comun.excepcion.DatosInvalidosException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Tarjeta 22: monedas a las que se puede proyectar, con su codigo ISO y su codigo en el Banguat.
 * Solo estan el dolar y las 10 monedas cuya forma de cotizar se verifico (ver ConvencionCotizacion).
 */
public enum Moneda {

	USD(2, "Dolar estadounidense"),
	EUR(24, "Euro"),
	GBP(9, "Libra esterlina"),
	JPY(3, "Yen japones"),
	CHF(5, "Franco suizo"),
	CAD(7, "Dolar canadiense"),
	MXN(18, "Peso mexicano"),
	CRC(16, "Colon costarricense"),
	HNL(19, "Lempira hondureno"),
	BRL(30, "Real brasileno"),
	COP(38, "Peso colombiano");

	private final int codigoBanguat;
	private final String nombre;

	Moneda(int codigoBanguat, String nombre) {
		this.codigoBanguat = codigoBanguat;
		this.nombre = nombre;
	}

	public int codigoBanguat() {
		return codigoBanguat;
	}

	public String nombre() {
		return nombre;
	}

	public boolean esDolar() {
		return codigoBanguat == ConvencionCotizacion.DOLAR;
	}

	/** Como se cotiza frente al dolar. El dolar mismo se cotiza en quetzales por dolar. */
	public String convencion() {
		return esDolar() ? "QUETZALES_POR_DOLAR"
				: ConvencionCotizacion.de(codigoBanguat).map(Enum::name).orElseThrow();
	}

	public static Optional<Moneda> deCodigo(String codigo) {
		if (codigo == null) {
			return Optional.empty();
		}
		String buscado = codigo.strip().toUpperCase(Locale.ROOT);
		return Arrays.stream(values()).filter(m -> m.name().equals(buscado)).findFirst();
	}

	/** Lee el codigo ISO de la peticion; si falta o no se reconoce, 400 sin repetir el valor recibido. */
	public static Moneda requerida(String codigo) {
		if (codigo == null || codigo.isBlank()) {
			throw DatosInvalidosException.de("moneda", "Es obligatoria. Valores permitidos: " + permitidas());
		}
		return deCodigo(codigo).orElseThrow(() -> DatosInvalidosException.de("moneda",
				"Moneda no soportada. Valores permitidos: " + permitidas()));
	}

	public static String permitidas() {
		return String.join(", ", Arrays.stream(values()).map(Enum::name).toList());
	}

}
