package com.proyecto.tramites.comun.excepcion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 401 y 403 salen de la cadena de seguridad, antes de llegar a los controladores, asi que no pasan por
 * el manejador global. Aqui se escriben con el mismo formato Problem Details.
 * Sin token valido: 401. Con token pero sin permiso: 403.
 */
@Component
public class ProblemaSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

	@Override
	public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException ex)
			throws IOException {
		bearer.commence(peticion, respuesta, ex); // conserva el encabezado WWW-Authenticate
		escribir(respuesta, 401, "No autenticado", "Falta el token de acceso o no es valido");
	}

	@Override
	public void handle(HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException ex)
			throws IOException {
		escribir(respuesta, 403, "Acceso denegado", "No tienes permiso para realizar esta accion");
	}

	private static void escribir(HttpServletResponse respuesta, int estado, String titulo, String detalle)
			throws IOException {
		respuesta.setStatus(estado);
		respuesta.setContentType("application/problem+json");
		respuesta.setCharacterEncoding("UTF-8");
		respuesta.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + estado
				+ ",\"detail\":\"" + detalle + "\"}");
	}

}
