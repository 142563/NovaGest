package com.proyecto.tramites.divisas.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/**
 * Como expresa el Banguat el valor de cada moneda frente al dolar. No es igual para todas:
 * la mayoria viene en unidades por 1 dolar, pero el euro y la libra vienen en dolares por 1 unidad
 * (hallazgo de docs/investigacion-apis-publicas.md, verificado el 02/10/2026 contra una segunda fuente).
 * Solo se registran las monedas verificadas; las demas no se convierten hasta confirmarlas.
 */
public enum ConvencionCotizacion {

	/** Valor = unidades de la moneda por 1 dolar (yen, peso mexicano, colon, etc.). */
	UNIDADES_POR_DOLAR,

	/** Valor = dolares por 1 unidad de la moneda (euro y libra esterlina). */
	DOLARES_POR_UNIDAD;

	/** Codigo del dolar en el Banguat; su valor es quetzales por dolar. */
	public static final int DOLAR = 2;

	private static final int ESCALA = 6;

	/** Monedas verificadas una por una. Codigo del Banguat -> convencion. */
	private static final Map<Integer, ConvencionCotizacion> VERIFICADAS = Map.ofEntries(
			Map.entry(3, UNIDADES_POR_DOLAR), // Yen
			Map.entry(5, UNIDADES_POR_DOLAR), // Franco suizo
			Map.entry(7, UNIDADES_POR_DOLAR), // Dolar canadiense
			Map.entry(9, DOLARES_POR_UNIDAD), // Libra esterlina
			Map.entry(16, UNIDADES_POR_DOLAR), // Colon costarricense
			Map.entry(18, UNIDADES_POR_DOLAR), // Peso mexicano
			Map.entry(19, UNIDADES_POR_DOLAR), // Lempira
			Map.entry(24, DOLARES_POR_UNIDAD), // Euro
			Map.entry(30, UNIDADES_POR_DOLAR), // Real brasileno
			Map.entry(38, UNIDADES_POR_DOLAR)); // Peso colombiano

	/** Convencion de una moneda; vacio si todavia no se verifico (no se debe convertir con ella). */
	public static Optional<ConvencionCotizacion> de(int codigoMoneda) {
		return Optional.ofNullable(VERIFICADAS.get(codigoMoneda));
	}

	/**
	 * Convierte quetzales a esta moneda: primero a dolares con el tipo de cambio del dolar y luego
	 * aplica el valor de la moneda segun su convencion.
	 *
	 * @param quetzales monto en quetzales
	 * @param quetzalesPorDolar tipo de cambio de referencia del dolar (moneda 2)
	 * @param valorMoneda valor publicado por el Banguat para la moneda destino
	 */
	public BigDecimal convertirDesdeQuetzales(BigDecimal quetzales, BigDecimal quetzalesPorDolar,
			BigDecimal valorMoneda) {
		if (quetzalesPorDolar.signum() <= 0 || valorMoneda.signum() <= 0) {
			throw new IllegalArgumentException("El tipo de cambio debe ser mayor que cero");
		}
		BigDecimal dolares = quetzales.divide(quetzalesPorDolar, ESCALA + 4, RoundingMode.HALF_EVEN);
		BigDecimal resultado = switch (this) {
			case UNIDADES_POR_DOLAR -> dolares.multiply(valorMoneda);
			case DOLARES_POR_UNIDAD -> dolares.divide(valorMoneda, ESCALA + 4, RoundingMode.HALF_EVEN);
		};
		return resultado.setScale(ESCALA, RoundingMode.HALF_EVEN);
	}

}
