package com.proyecto.tramites.comun.config;

import com.proyecto.tramites.auth.controller.AuthController;
import com.proyecto.tramites.comun.util.SoloAutenticado;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Tarjeta 5: documentacion de la API (OpenAPI 3 en /v3/api-docs y Swagger UI en /swagger-ui.html).
 * Cada operacion muestra su permiso, leido del mismo @PreAuthorize que lo exige, para que la
 * documentacion no se desfase del codigo. El boton "Authorize" recibe el token de acceso (JWT).
 */
@Configuration
public class OpenApiConfig {

	static final String ESQUEMA = "bearer-jwt";

	private static final Pattern AUTORIDAD = Pattern.compile("hasAuthority\\('([^']+)'\\)");

	@Bean
	OpenAPI novaGestOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("NovaGest API")
						.version("0.0.1")
						.description("Sistema de tramites ciudadanos. Inicia sesion en POST /api/auth/login, copia el "
								+ "accessToken y pegalo en Authorize. Cada operacion indica el permiso que exige."))
				.components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("Token de acceso de POST /api/auth/login (vence a los 15 minutos)")))
				.addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
	}

	/** Agrega a cada operacion su permiso y las respuestas 401/403 que puede devolver la seguridad. */
	@Bean
	OperationCustomizer permisoEnCadaOperacion() {
		return (operacion, metodo) -> {
			// registro, login y refresh son publicos; logout exige sesion (lleva @SoloAutenticado) y sigue la ruta general
			if (AuthController.class.isAssignableFrom(metodo.getBeanType())
					&& !AnnotatedElementUtils.hasAnnotation(metodo.getMethod(), SoloAutenticado.class)) {
				operacion.setSecurity(List.of());
				return operacion.description(unir(operacion.getDescription(), "Publico: no requiere token."));
			}

			PreAuthorize regla = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), PreAuthorize.class);
			if (regla == null) {
				regla = AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), PreAuthorize.class);
			}
			String permiso = regla == null ? "Requiere sesion iniciada (cualquier usuario autenticado)."
					: "Permiso: " + permisoLegible(regla.value());

			ApiResponses respuestas = operacion.getResponses() == null ? new ApiResponses() : operacion.getResponses();
			respuestas.addApiResponse("401", new ApiResponse().description("Falta el token de acceso o no es valido"));
			if (regla != null) {
				respuestas.addApiResponse("403", new ApiResponse().description("No tienes permiso para esta accion"));
			}
			operacion.setResponses(respuestas);
			return operacion.description(unir(operacion.getDescription(), permiso));
		};
	}

	/** De "hasAuthority('tramite:leer')" deja "tramite:leer"; cualquier otra expresion se muestra tal cual. */
	static String permisoLegible(String expresion) {
		Matcher coincidencia = AUTORIDAD.matcher(expresion);
		StringBuilder permisos = new StringBuilder();
		while (coincidencia.find()) {
			if (!permisos.isEmpty()) {
				permisos.append(", ");
			}
			permisos.append(coincidencia.group(1));
		}
		return permisos.isEmpty() ? expresion : permisos.toString();
	}

	private static String unir(String descripcion, String linea) {
		return descripcion == null || descripcion.isBlank() ? linea : descripcion + "\n\n" + linea;
	}

}
