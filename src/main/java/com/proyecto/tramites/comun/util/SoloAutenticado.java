package com.proyecto.tramites.comun.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint que solo exige haber iniciado sesion, sin un permiso concreto (por ejemplo, ver el
 * propio perfil). Todo endpoint debe llevar @PreAuthorize("hasAuthority('recurso:accion')") o esta marca:
 * el test de la matriz de roles falla si encuentra un endpoint sin ninguna de las dos.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SoloAutenticado {

}
