package com.proyecto.tramites.tramites.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Datos para crear o reemplazar un tipo de tramite. Los montos son decimales, nunca double. */
public record TipoTramiteRequest(
		@NotBlank @Size(max = 100) String nombre,
		@Size(max = 255) String descripcion,
		@NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal monto,
		Boolean activo) {

	/** Si no se indica, el tipo queda activo. */
	public boolean activoONulo() {
		return activo == null || activo;
	}

}
