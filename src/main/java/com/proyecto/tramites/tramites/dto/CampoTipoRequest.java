package com.proyecto.tramites.tramites.dto;

import com.proyecto.tramites.tramites.entity.TipoDato;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Un campo que declara un tipo de tramite. La clave va en minusculas con guiones bajos (por ejemplo
 * ingreso_mensual). El patron (expresion regular) solo aplica a TEXTO y el minimo solo a NUMERO.
 */
public record CampoTipoRequest(
		@NotBlank @Size(max = 50) @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "debe ir en minusculas, con numeros o guion bajo") String clave,
		@NotBlank @Size(max = 100) String etiqueta,
		@NotNull TipoDato tipoDato,
		Boolean obligatorio,
		@Size(max = 200) String patron,
		@DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal minimo,
		Integer orden) {

	public boolean obligatorioONulo() {
		return obligatorio != null && obligatorio;
	}

}
