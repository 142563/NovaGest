package com.proyecto.tramites.comun.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Revisa el permiso del endpoint ANTES de que Spring lea y valide el cuerpo de la peticion.
 * Con @PreAuthorize solo, la validacion corre primero: quien no tiene permiso recibiria un 400 que
 * revela las reglas del cuerpo en vez de un 403. Con este interceptor el orden es siempre:
 * sin token 401, sin permiso 403, y solo despues validacion 400. @PreAuthorize sigue en cada metodo
 * como segunda barrera y como documentacion.
 */
public class PermisoPreviaInterceptor implements HandlerInterceptor {

	/** Solo reconoce el formato simple hasAuthority('recurso:accion'); otras expresiones las decide @PreAuthorize. */
	public static final Pattern PERMISO_SIMPLE = Pattern.compile("^hasAuthority\\('([a-z_]+:[a-z]+)'\\)$");

	@Override
	public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
		if (!(manejador instanceof HandlerMethod metodo)) {
			return true;
		}
		PreAuthorize regla = metodo.getMethodAnnotation(PreAuthorize.class);
		if (regla == null) {
			return true;
		}
		Matcher coincidencia = PERMISO_SIMPLE.matcher(regla.value().trim());
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (!coincidencia.matches() || autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken
				|| !autenticacion.isAuthenticated()) {
			return true; // sin sesion ya respondio 401 la cadena de seguridad
		}
		String permiso = coincidencia.group(1);
		boolean lotiene = autenticacion.getAuthorities().stream().anyMatch(a -> permiso.equals(a.getAuthority()));
		if (!lotiene) {
			throw new AccessDeniedException("Falta el permiso " + permiso);
		}
		return true;
	}

}
