package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Tarjeta 4: todo error sale como Problem Details (application/problem+json), sin datos internos. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProblemDetailsTests {

	private static final String CLAVE = "clave-segura-123";

	@Autowired
	private MockMvc mvc;

	private MockHttpServletResponse enviar(String metodo, String ruta, String token, String cuerpo) throws Exception {
		var peticion = switch (metodo) {
			case "POST" -> post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo == null ? "" : cuerpo);
			default -> get(ruta);
		};
		if (token != null) {
			peticion = peticion.header("Authorization", "Bearer " + token);
		}
		return mvc.perform(peticion).andReturn().getResponse();
	}

	private String tokenDeUnCiudadano() throws Exception {
		enviar("POST", "/api/auth/registro", null,
				"{\"nombre\":\"Ana Lopez\",\"correo\":\"ana@demo.com\",\"clave\":\"" + CLAVE + "\"}");
		String cuerpo = enviar("POST", "/api/auth/login", null,
				"{\"correo\":\"ana@demo.com\",\"clave\":\"" + CLAVE + "\"}").getContentAsString();
		return JsonPath.read(cuerpo, "$.accessToken");
	}

	private static void esProblema(MockHttpServletResponse respuesta, int estado) throws Exception {
		assertThat(respuesta.getStatus()).isEqualTo(estado);
		assertThat(respuesta.getContentType()).startsWith("application/problem+json");
		String cuerpo = respuesta.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.status")).isEqualTo(estado);
		assertThat(JsonPath.<String>read(cuerpo, "$.title")).isNotBlank();
	}

	@Test
	void unaValidacionFallidaEs400ConCampoYMotivoSinElValorRechazado() throws Exception {
		MockHttpServletResponse r = enviar("POST", "/api/auth/registro", null,
				"{\"nombre\":\"Ana\",\"correo\":\"ana@demo.com\",\"clave\":\"secr-37\"}");

		esProblema(r, 400);
		String cuerpo = r.getContentAsString();
		List<Map<String, String>> errores = JsonPath.read(cuerpo, "$.errores");
		assertThat(errores).hasSize(1);
		assertThat(errores.get(0)).containsEntry("campo", "clave");
		assertThat(errores.get(0).get("motivo")).isNotBlank();
		assertThat(cuerpo).doesNotContain("secr-37");
	}

	@Test
	void variosCamposInvalidosSeListanTodos() throws Exception {
		MockHttpServletResponse r = enviar("POST", "/api/auth/registro", null,
				"{\"nombre\":\"\",\"correo\":\"no-es-correo\",\"clave\":\"1\"}");

		esProblema(r, 400);
		List<String> campos = JsonPath.read(r.getContentAsString(), "$.errores[*].campo");
		assertThat(campos).contains("nombre", "correo", "clave");
	}

	@Test
	void unJsonMalformadoEs400() throws Exception {
		esProblema(enviar("POST", "/api/auth/login", null, "{esto no es json"), 400);
	}

	@Test
	void unaClaveIncorrectaEs401ConProblemDetails() throws Exception {
		MockHttpServletResponse r = enviar("POST", "/api/auth/login", null,
				"{\"correo\":\"nadie@demo.com\",\"clave\":\"clave-segura-123\"}");

		esProblema(r, 401);
		assertThat(JsonPath.<String>read(r.getContentAsString(), "$.detail")).isEqualTo("Credenciales invalidas");
	}

	@Test
	void unCorreoRepetidoEs409ConProblemDetails() throws Exception {
		String cuerpo = "{\"nombre\":\"Ana Lopez\",\"correo\":\"ana@demo.com\",\"clave\":\"" + CLAVE + "\"}";
		enviar("POST", "/api/auth/registro", null, cuerpo);

		esProblema(enviar("POST", "/api/auth/registro", null, cuerpo), 409);
	}

	@Test
	void sinTokenEs401ConProblemDetailsYEncabezadoWwwAuthenticate() throws Exception {
		MockHttpServletResponse r = enviar("GET", "/api/usuarios/yo", null, null);

		esProblema(r, 401);
		assertThat(r.getHeader("WWW-Authenticate")).startsWith("Bearer");
	}

	@Test
	void conTokenInvalidoEs401ConProblemDetails() throws Exception {
		esProblema(enviar("GET", "/api/usuarios/yo", "esto.no.es-un-jwt", null), 401);
	}

	@Test
	void conTokenPeroSinPermisoEs403ConProblemDetails() throws Exception {
		MockHttpServletResponse r = enviar("GET", "/api/_prueba/solo-administrador", tokenDeUnCiudadano(), null);

		esProblema(r, 403);
		assertThat(JsonPath.<String>read(r.getContentAsString(), "$.title")).isEqualTo("Acceso denegado");
	}

	@Test
	void unaRutaInexistenteConTokenEs404ConProblemDetails() throws Exception {
		esProblema(enviar("GET", "/api/ruta-que-no-existe", tokenDeUnCiudadano(), null), 404);
	}

	@Test
	void unMetodoNoPermitidoEs405ConProblemDetails() throws Exception {
		esProblema(enviar("POST", "/api/usuarios/yo", tokenDeUnCiudadano(), "{}"), 405);
	}

	@Test
	void unErrorInesperadoEs500GenericoSinFiltrarElMensajeInterno() throws Exception {
		MockHttpServletResponse r = enviar("GET", "/api/_prueba/falla", tokenDeUnCiudadano(), null);

		esProblema(r, 500);
		String cuerpo = r.getContentAsString();
		assertThat(cuerpo).doesNotContain("detalle-secreto-interno-123").doesNotContain("IllegalStateException")
				.doesNotContain("at com.proyecto");
		assertThat(JsonPath.<String>read(cuerpo, "$.detail")).isEqualTo("Ocurrio un error inesperado");
	}

}
