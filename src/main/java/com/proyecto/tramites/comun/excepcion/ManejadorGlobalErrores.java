package com.proyecto.tramites.comun.excepcion;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Formato unico de errores para toda la API: Problem Details (application/problem+json).
 * Los errores estandar de Spring MVC (JSON malformado, metodo no permitido, ruta inexistente,
 * ResponseStatusException) los resuelve la clase base con ese mismo formato.
 * Regla: nunca se devuelve ni se registra el valor rechazado de un campo (puede ser una clave).
 */
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

	/** 400 con la lista de campos invalidos: campo y motivo, sin el valor rechazado. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"La solicitud tiene campos invalidos");
		problema.setTitle("Solicitud invalida");
		List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of(
						"campo", error.getField(),
						"motivo", error.getDefaultMessage() == null ? "valor invalido" : error.getDefaultMessage()))
				.toList();
		problema.setProperty("errores", errores);
		return handleExceptionInternal(ex, problema, headers, status, request);
	}

	/** 400 cuando los datos incumplen una regla de negocio: lista de campo y motivo, sin valores rechazados. */
	@ExceptionHandler(DatosInvalidosException.class)
	ProblemDetail datosInvalidos(DatosInvalidosException ex) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"La solicitud tiene campos invalidos");
		problema.setTitle("Solicitud invalida");
		problema.setProperty("errores", ex.getErrores());
		return problema;
	}

	/** 400 cuando se espera un archivo (multipart/form-data) y la peticion no lo trae o llega mal formada. */
	@ExceptionHandler(MultipartException.class)
	ProblemDetail archivoNoValido(MultipartException ex) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"La peticion debe enviarse como multipart/form-data con el archivo en el campo indicado");
		problema.setTitle("Solicitud invalida");
		return problema;
	}

	/** 409 cuando la base rechaza el dato (por ejemplo un valor unico repetido). Sin detalles internos. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail conflictoDeDatos(DataIntegrityViolationException ex) {
		LOG.warn("Conflicto de integridad de datos: {}", ex.getClass().getSimpleName());
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"La operacion choca con el estado actual de los datos");
		problema.setTitle("Conflicto");
		return problema;
	}

	/** 500 generico: el detalle real queda en el log del servidor, nunca en la respuesta. */
	@ExceptionHandler(Exception.class)
	ProblemDetail inesperado(Exception ex) throws Exception {
		// 401 y 403 los resuelve la cadena de seguridad (ProblemaSeguridad), no este metodo
		if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
			throw ex;
		}
		LOG.error("Error no controlado", ex);
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Ocurrio un error inesperado");
		problema.setTitle("Error interno");
		return problema;
	}

}
