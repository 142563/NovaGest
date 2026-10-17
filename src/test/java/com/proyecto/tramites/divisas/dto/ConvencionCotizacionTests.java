package com.proyecto.tramites.divisas.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Tarjeta 20: el euro y la libra se cotizan en dolares por unidad; el resto, en unidades por dolar.
 * Valores del 02/10/2026 (docs/investigacion-apis-publicas.md): dolar 7.64136, euro 1.1275, yen 157.52.
 */
class ConvencionCotizacionTests {

	private static final BigDecimal TRES_MIL = new BigDecimal("3000");
	private static final BigDecimal DOLAR = new BigDecimal("7.64136");

	@Test
	void elEuroYLaLibraSeCotizanEnDolaresPorUnidad() {
		assertThat(ConvencionCotizacion.de(24)).contains(ConvencionCotizacion.DOLARES_POR_UNIDAD);
		assertThat(ConvencionCotizacion.de(9)).contains(ConvencionCotizacion.DOLARES_POR_UNIDAD);
	}

	@Test
	void elYenYElPesoMexicanoSeCotizanEnUnidadesPorDolar() {
		assertThat(ConvencionCotizacion.de(3)).contains(ConvencionCotizacion.UNIDADES_POR_DOLAR);
		assertThat(ConvencionCotizacion.de(18)).contains(ConvencionCotizacion.UNIDADES_POR_DOLAR);
	}

	@Test
	void unaMonedaSinVerificarNoTieneConvencion() {
		assertThat(ConvencionCotizacion.de(26)).isEmpty(); // DEG
		assertThat(ConvencionCotizacion.de(4)).isEmpty(); // Francos belgas (ya no existe)
		assertThat(ConvencionCotizacion.de(ConvencionCotizacion.DOLAR)).isEmpty(); // el dolar se usa directo
	}

	@Test
	void tresMilQuetzalesSonUnos348Euros() {
		BigDecimal euros = ConvencionCotizacion.DOLARES_POR_UNIDAD.convertirDesdeQuetzales(TRES_MIL, DOLAR,
				new BigDecimal("1.1275"));

		assertThat(euros.doubleValue()).isCloseTo(348.204, within(0.001));
	}

	@Test
	void tresMilQuetzalesSonUnos61842Yenes() {
		BigDecimal yenes = ConvencionCotizacion.UNIDADES_POR_DOLAR.convertirDesdeQuetzales(TRES_MIL, DOLAR,
				new BigDecimal("157.52"));

		assertThat(yenes.doubleValue()).isCloseTo(61842.39, within(0.01));
	}

	@Test
	void aplicarLaFormulaEquivocadaAlEuroDariaUnMontoMuyDistinto() {
		BigDecimal correcto = ConvencionCotizacion.DOLARES_POR_UNIDAD.convertirDesdeQuetzales(TRES_MIL, DOLAR,
				new BigDecimal("1.1275"));
		BigDecimal equivocado = ConvencionCotizacion.UNIDADES_POR_DOLAR.convertirDesdeQuetzales(TRES_MIL, DOLAR,
				new BigDecimal("1.1275"));

		assertThat(equivocado).isGreaterThan(correcto);
		assertThat(equivocado.doubleValue()).isCloseTo(442.66, within(0.01));
	}

	@Test
	void noAceptaUnTipoDeCambioCeroONegativo() {
		assertThatThrownBy(() -> ConvencionCotizacion.UNIDADES_POR_DOLAR.convertirDesdeQuetzales(TRES_MIL,
				BigDecimal.ZERO, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ConvencionCotizacion.DOLARES_POR_UNIDAD.convertirDesdeQuetzales(TRES_MIL, DOLAR,
				new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
	}

}
