package com.proyecto.tramites.tramites.service;

import com.proyecto.tramites.comun.excepcion.DatosInvalidosException;
import com.proyecto.tramites.tramites.entity.CampoTipoTramite;
import com.proyecto.tramites.tramites.entity.TipoTramite;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Valida los datos de un tramite contra los campos que declara su tipo y los convierte de y a JSON.
 * Regla de oro: los errores dicen el campo y el motivo, nunca el valor recibido (puede ser un dato personal).
 */
@Component
public class ValidadorDatosTramite {

	private static final TypeReference<LinkedHashMap<String, Object>> TIPO_MAPA = new TypeReference<>() {
	};
	private static final int LARGO_MAXIMO_TEXTO = 255;
	private static final BigDecimal TOPE_NUMERO = new BigDecimal("1000000000000");
	private static final Pattern CLAVE_SEGURA = Pattern.compile("^[A-Za-z0-9_]{1,50}$");

	private final JsonMapper json;

	public ValidadorDatosTramite(JsonMapper json) {
		this.json = json;
	}

	/**
	 * Revisa que cada dato sea de un campo declarado y tenga el tipo y formato correctos. No exige que esten
	 * todos los obligatorios: eso se pide al enviar el tramite (ver exigirCompletitud). Devuelve los datos
	 * normalizados (texto sin espacios sobrantes, numeros con 2 decimales).
	 */
	public Map<String, Object> validar(TipoTramite tipo, Map<String, Object> datos) {
		Map<String, CampoTipoTramite> declarados = new LinkedHashMap<>();
		tipo.getCampos().forEach(campo -> declarados.put(campo.getClave(), campo));
		DatosInvalidosException.Acumulador errores = new DatosInvalidosException.Acumulador();
		Map<String, Object> normalizados = new LinkedHashMap<>();
		if (datos != null) {
			datos.forEach((clave, valor) -> {
				CampoTipoTramite campo = declarados.get(clave);
				if (campo == null) {
					String nombre = clave != null && CLAVE_SEGURA.matcher(clave).matches() ? clave : "(clave no valida)";
					errores.agregar("datos." + nombre, "El tipo de tramite no declara este campo");
				}
				else if (valor != null) {
					Object normalizado = normalizar(campo, valor, errores);
					if (normalizado != null) {
						normalizados.put(clave, normalizado);
					}
				}
			});
		}
		errores.lanzarSiHayErrores();
		return normalizados;
	}

	/** Exige que esten todos los campos obligatorios del tipo (se usa al enviar el tramite, tarjeta 16). */
	public void exigirCompletitud(TipoTramite tipo, Map<String, Object> datos) {
		DatosInvalidosException.Acumulador errores = new DatosInvalidosException.Acumulador();
		for (CampoTipoTramite campo : tipo.getCampos()) {
			if (campo.isObligatorio() && (datos == null || datos.get(campo.getClave()) == null)) {
				errores.agregar("datos." + campo.getClave(), "Campo obligatorio");
			}
		}
		errores.lanzarSiHayErrores();
	}

	public String aJson(Map<String, Object> datos) {
		return json.writeValueAsString(datos == null ? Map.of() : datos);
	}

	public Map<String, Object> deJson(String texto) {
		return texto == null || texto.isBlank() ? new LinkedHashMap<>() : json.readValue(texto, TIPO_MAPA);
	}

	private Object normalizar(CampoTipoTramite campo, Object valor, DatosInvalidosException.Acumulador errores) {
		String ruta = "datos." + campo.getClave();
		switch (campo.getTipoDato()) {
			case TEXTO -> {
				if (!(valor instanceof String texto)) {
					errores.agregar(ruta, "Debe ser texto");
					return null;
				}
				String limpio = texto.trim();
				if (limpio.isEmpty()) {
					return null;
				}
				if (limpio.length() > LARGO_MAXIMO_TEXTO) {
					errores.agregar(ruta, "Maximo " + LARGO_MAXIMO_TEXTO + " caracteres");
					return null;
				}
				if (campo.getPatron() != null && !Pattern.compile(campo.getPatron()).matcher(limpio).matches()) {
					errores.agregar(ruta, "No cumple el formato esperado");
					return null;
				}
				return limpio;
			}
			case NUMERO -> {
				if (!(valor instanceof Number numero)) {
					errores.agregar(ruta, "Debe ser un numero");
					return null;
				}
				BigDecimal decimal = new BigDecimal(numero.toString());
				if (decimal.stripTrailingZeros().scale() > 2) {
					errores.agregar(ruta, "Maximo 2 decimales");
					return null;
				}
				if (decimal.abs().compareTo(TOPE_NUMERO) >= 0) {
					errores.agregar(ruta, "El numero es demasiado grande");
					return null;
				}
				if (campo.getMinimo() != null && decimal.compareTo(campo.getMinimo()) < 0) {
					errores.agregar(ruta, "Debe ser mayor o igual a " + campo.getMinimo().toPlainString());
					return null;
				}
				return decimal.setScale(2, RoundingMode.UNNECESSARY);
			}
			case FECHA -> {
				if (!(valor instanceof String texto)) {
					errores.agregar(ruta, "Debe ser una fecha en formato AAAA-MM-DD");
					return null;
				}
				try {
					return LocalDate.parse(texto.trim()).toString();
				}
				catch (DateTimeParseException e) {
					errores.agregar(ruta, "Debe ser una fecha valida en formato AAAA-MM-DD");
					return null;
				}
			}
			case BOOLEANO -> {
				if (!(valor instanceof Boolean)) {
					errores.agregar(ruta, "Debe ser verdadero o falso");
					return null;
				}
				return valor;
			}
			default -> {
				return null;
			}
		}
	}

}
