package com.proyecto.tramites.tramites.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** Datos para abrir un tramite en Borrador. Los datos se validan contra los campos del tipo elegido. */
public record TramiteRequest(@NotNull Long tipoId, Map<String, Object> datos) {

}
