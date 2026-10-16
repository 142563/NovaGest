package com.proyecto.tramites.tramites.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Datos para crear o reemplazar un tipo de tramite. Los montos son decimales, nunca double.
 * Los campos son opcionales: si no se envian al editar, los del tipo no cambian; si se envian, reemplazan
 * a los anteriores.
 */
public record TipoTramiteRequest(
		@NotBlank @Size(max = 100) String nombre,
		@Size(max = 255) String descripcion,
		@NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal monto,
		Boolean activo,
		@Size(max = 30) List<@Valid CampoTipoRequest> campos) {

	/** Si no se indica, el tipo queda activo. */
	public boolean activoONulo() {
		return activo == null || activo;
	}

}
