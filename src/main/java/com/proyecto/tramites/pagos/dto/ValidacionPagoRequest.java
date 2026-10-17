package com.proyecto.tramites.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Decision del gestor sobre un pago. El comentario es obligatorio al rechazar (se valida en el servicio). */
public record ValidacionPagoRequest(@NotNull DecisionPago decision, @Size(max = 500) String comentario) {

}
