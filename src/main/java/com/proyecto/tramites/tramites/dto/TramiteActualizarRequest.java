package com.proyecto.tramites.tramites.dto;

import java.util.Map;

/** Nuevos datos del tramite: reemplazan por completo a los anteriores. El estado no se cambia aqui. */
public record TramiteActualizarRequest(Map<String, Object> datos) {

}
