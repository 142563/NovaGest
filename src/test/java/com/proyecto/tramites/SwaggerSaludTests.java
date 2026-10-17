package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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

/**
 * Tarjeta 5: /actuator/health y la documentacion de la API responden sin token, sin exponer detalles
 * internos, y cada operacion documenta el permiso que exige.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SwaggerSaludTests {

	private static final String CLAVE = "clave-segura-123";

	@Autowired
	private MockMvc mvc;

	private MockHttpServletResponse pedir(String ruta, String token) throws Exception {
		var peticion = get(ruta);
		if (token != null) {
			peticion = peticion.header("Authorization", "Bearer " + token);
		}
		return mvc.perform(peticion).andReturn().getResponse();
	}

	private String documentacion() throws Exception {
		MockHttpServletResponse respuesta = pedir("/v3/api-docs", null);
		assertThat(respuesta.getStatus()).isEqualTo(200);
		return respuesta.getContentAsString(StandardCharsets.UTF_8);
	}

	private String tokenDeUnCiudadano() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana Lopez\",\"correo\":\"ana@demo.com\",\"clave\":\"" + CLAVE + "\"}"));
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"ana@demo.com\",\"clave\":\"" + CLAVE + "\"}")).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(cuerpo, "$.accessToken");
	}

	@Test
	void laSaludRespondeSinTokenYEstaArriba() throws Exception {
		MockHttpServletResponse respuesta = pedir("/actuator/health", null);

		assertThat(respuesta.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<String>read(respuesta.getContentAsString(), "$.status")).isEqualTo("UP");
	}

	@Test
	void laSaludNoRevelaDetallesInternos() throws Exception {
		String cuerpo = pedir("/actuator/health", null).getContentAsString();

		assertThat(cuerpo).doesNotContain("components").doesNotContain("details").doesNotContain("postgres")
				.doesNotContain("diskSpace");
	}

	@Test
	void otrosEndpointsDelActuatorNoSeExponenNiConToken() throws Exception {
		assertThat(pedir("/actuator/env", null).getStatus()).isEqualTo(401);
		assertThat(pedir("/actuator/beans", null).getStatus()).isEqualTo(401);

		String token = tokenDeUnCiudadano();
		assertThat(pedir("/actuator/env", token).getStatus()).isNotEqualTo(200);
		assertThat(pedir("/actuator/beans", token).getStatus()).isNotEqualTo(200);
	}

	@Test
	void laDocumentacionOpenApiRespondeSinToken() throws Exception {
		String doc = documentacion();

		assertThat(JsonPath.<String>read(doc, "$.info.title")).isEqualTo("NovaGest API");
		assertThat(JsonPath.<Map<String, Object>>read(doc, "$.paths")).containsKeys("/api/tramites",
				"/api/tramites/{id}", "/api/tipos-tramite", "/api/auth/login", "/api/usuarios/yo");
	}

	@Test
	void laDocumentacionDeclaraElTokenBearer() throws Exception {
		String doc = documentacion();

		assertThat(JsonPath.<String>read(doc, "$.components.securitySchemes.bearer-jwt.scheme")).isEqualTo("bearer");
		assertThat(JsonPath.<String>read(doc, "$.components.securitySchemes.bearer-jwt.bearerFormat")).isEqualTo("JWT");
	}

	@Test
	void cadaOperacionMuestraElPermisoQueExige() throws Exception {
		String doc = documentacion();

		assertThat(JsonPath.<String>read(doc, "$.paths['/api/tramites'].get.description"))
				.contains("Permiso: tramite:leer");
		assertThat(JsonPath.<String>read(doc, "$.paths['/api/tramites'].post.description"))
				.contains("Permiso: tramite:crear");
		assertThat(JsonPath.<String>read(doc, "$.paths['/api/tramites/{id}'].delete.description"))
				.contains("Permiso: tramite:eliminar");
		assertThat(JsonPath.<String>read(doc, "$.paths['/api/tipos-tramite'].post.description"))
				.contains("Permiso: tipo_tramite:crear");
		assertThat(JsonPath.<Map<String, Object>>read(doc, "$.paths['/api/tramites'].get.responses"))
				.containsKeys("401", "403");
	}

	@Test
	void registroYLoginAparecenComoPublicos() throws Exception {
		String doc = documentacion();

		assertThat(JsonPath.<List<Object>>read(doc, "$.paths['/api/auth/login'].post.security")).isEmpty();
		assertThat(JsonPath.<List<Object>>read(doc, "$.paths['/api/auth/registro'].post.security")).isEmpty();
		assertThat(JsonPath.<String>read(doc, "$.paths['/api/auth/login'].post.description")).contains("Publico");
	}

	@Test
	void elPerfilPideSesionAunqueNoTengaPermisoEspecifico() throws Exception {
		String doc = documentacion();

		assertThat(JsonPath.<String>read(doc, "$.paths['/api/usuarios/yo'].get.description"))
				.contains("Requiere sesion iniciada");
		assertThat(JsonPath.<Map<String, Object>>read(doc, "$.paths['/api/usuarios/yo'].get.responses"))
				.containsKey("401");
	}

	@Test
	void laPantallaDeSwaggerRespondeSinToken() throws Exception {
		assertThat(pedir("/swagger-ui/index.html", null).getStatus()).isEqualTo(200);
		assertThat(pedir("/swagger-ui.html", null).getStatus()).isIn(200, 302);
	}

	@Test
	void laDocumentacionNoAbreElRestoDeLaApi() throws Exception {
		assertThat(pedir("/api/tramites", null).getStatus()).isEqualTo(401);
	}

}
