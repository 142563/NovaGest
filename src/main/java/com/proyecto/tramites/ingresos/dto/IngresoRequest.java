package com.proyecto.tramites.ingresos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos para crear o reemplazar un ingreso. Monto en quetzales, decimal (nunca double), mayor que cero y con
 * dos decimales como maximo. La fecha no puede ser futura (lo valida el servicio con la hora de Guatemala).
 */
public record IngresoRequest(
		@NotNull LocalDate fecha,
		@NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal montoGtq,
		@Size(max = 200) String descripcion) {
}
