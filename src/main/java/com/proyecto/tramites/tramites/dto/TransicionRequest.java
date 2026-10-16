package com.proyecto.tramites.tramites.dto;

import com.proyecto.tramites.tramites.entity.EstadoTramite;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pide pasar un tramite a otro estado. El comentario es obligatorio al observar o rechazar
 * (se valida en el servicio segun la transicion).
 */
public record TransicionRequest(@NotNull EstadoTramite estadoDestino, @Size(max = 500) String comentario) {

}
