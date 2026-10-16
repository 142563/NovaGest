package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.jayway.jsonpath.JsonPath;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.RolRepository;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Tarjeta 15: tramites con datos validados por tipo, alcance propio o total y borrado logico. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TramitesTests {

	private static final String CLAVE = "clave-segura-123";
	private static final String RUTA = "/api/tramites";
	private static final String DATOS_ORNATO_VALIDOS =
			"{\"dpi\":\"1234567890123\",\"ocupacion\":\"Comerciante\",\"ingreso_mensual\":3500.5}";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private RolRepository roles;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager em;

	private MockHttpServletResponse enviar(HttpMethod metodo, String ruta, String token, String cuerpo) throws Exception {
		var peticion = request(metodo, ruta);
		if (cuerpo != null) {
			peticion = peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		}
		if (token != null) {
			peticion = peticion.header("Authorization", "Bearer " + token);
		}
		return mvc.perform(peticion).andReturn().getResponse();
	}

	/** Registra un usuario, le deja solo el rol indicado, inicia sesion y devuelve su token. */
	private String tokenCon(String correo, String rol) throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(
				"{\"nombre\":\"Persona Prueba\",\"correo\":\"" + correo + "\",\"clave\":\"" + CLAVE + "\"}"));
		Usuario usuario = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull(correo).orElseThrow();
		usuario.getRoles().clear();
		usuario.getRoles().add(roles.findByNombre(rol).orElseThrow());
		usuarios.saveAndFlush(usuario);
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"clave\":\"" + CLAVE + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(cuerpo, "$.accessToken");
	}

	private long idDelTipo(String token, String nombre) throws Exception {
		String cuerpo = enviar(HttpMethod.GET, "/api/tipos-tramite?size=100", token, null).getContentAsString();
		List<Number> ids = JsonPath.read(cuerpo, "$.content[?(@.nombre=='" + nombre + "')].id");
		return ids.get(0).longValue();
	}

	private static String cuerpoTramite(long tipoId, String datos) {
		return "{\"tipoId\":" + tipoId + ",\"datos\":" + datos + "}";
	}

	private long crearTramite(String token, long tipoId, String datos) throws Exception {
		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, token, cuerpoTramite(tipoId, datos));
		assertThat(r.getStatus()).isEqualTo(201);
		return JsonPath.<Number>read(r.getContentAsString(), "$.id").longValue();
	}

	private void ponerEstado(long idTramite, String estado) {
		em.flush();
		jdbc.update("UPDATE tramite SET estado = ? WHERE id = ?", estado, idTramite);
		em.clear();
	}

	@Test
	void unCiudadanoCreaUnTramiteEnBorradorConDatosValidos() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");

		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, ana, cuerpoTramite(ornato, DATOS_ORNATO_VALIDOS));

		assertThat(r.getStatus()).isEqualTo(201);
		String cuerpo = r.getContentAsString();
		assertThat(JsonPath.<String>read(cuerpo, "$.estado")).isEqualTo("BORRADOR");
		assertThat(JsonPath.<String>read(cuerpo, "$.tipoNombre")).isEqualTo("Boleto de ornato");
		assertThat(JsonPath.<String>read(cuerpo, "$.datos.dpi")).isEqualTo("1234567890123");
		assertThat(JsonPath.<Number>read(cuerpo, "$.datos.ingreso_mensual").doubleValue()).isEqualTo(3500.5);
		String perfil = enviar(HttpMethod.GET, "/api/usuarios/yo", ana, null).getContentAsString();
		assertThat(JsonPath.<Number>read(cuerpo, "$.ciudadanoId").longValue())
				.isEqualTo(JsonPath.<Number>read(perfil, "$.id").longValue());
	}

	@Test
	void losDatosPuedenEstarIncompletosEnBorrador() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");

		assertThat(enviar(HttpMethod.POST, RUTA, ana, cuerpoTramite(ornato, "{\"dpi\":\"1234567890123\"}")).getStatus())
				.isEqualTo(201);
		assertThat(enviar(HttpMethod.POST, RUTA, ana, cuerpoTramite(ornato, "{}")).getStatus()).isEqualTo(201);
		assertThat(enviar(HttpMethod.POST, RUTA, ana, "{\"tipoId\":" + ornato + "}").getStatus()).isEqualTo(201);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"{\"dpi\":\"123\"}|datos.dpi",
			"{\"dpi\":1234567890123}|datos.dpi",
			"{\"color\":\"azul\"}|datos.color",
			"{\"ingreso_mensual\":-5}|datos.ingreso_mensual",
			"{\"ingreso_mensual\":\"mucho\"}|datos.ingreso_mensual",
			"{\"ingreso_mensual\":10.123}|datos.ingreso_mensual",
			"{\"ocupacion\":5}|datos.ocupacion",
			"{\"direccion\":true}|datos.direccion" })
	void losDatosQueNoCumplenElTipoSon400ConElCampo(String datos, String campoEsperado) throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");

		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, ana, cuerpoTramite(ornato, datos));

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(r.getContentType()).startsWith("application/problem+json");
		List<String> campos = JsonPath.read(r.getContentAsString(), "$.errores[*].campo");
		assertThat(campos).contains(campoEsperado);
	}

	@Test
	void variosErroresSeListanJuntos() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");

		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, ana,
				cuerpoTramite(ornato, "{\"dpi\":\"1\",\"ingreso_mensual\":-1,\"color\":\"x\"}"));

		List<String> campos = JsonPath.read(r.getContentAsString(), "$.errores[*].campo");
		assertThat(campos).contains("datos.dpi", "datos.ingreso_mensual", "datos.color");
	}

	@Test
	void unErrorNuncaDevuelveElValorRecibido() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");

		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, ana,
				cuerpoTramite(ornato, "{\"dpi\":\"SECRETO-9876543\",\"direccion\":12345,\"campo_raro\":\"OTRO-SECRETO\"}"));

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(r.getContentAsString()).doesNotContain("SECRETO-9876543").doesNotContain("OTRO-SECRETO");
	}

	@Test
	void elTextoSeLimpiaYLosNumerosQuedanConDosDecimales() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");

		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, ana,
				cuerpoTramite(ornato, "{\"ocupacion\":\"  Maestra  \",\"ingreso_mensual\":2500}"));

		String cuerpo = r.getContentAsString();
		assertThat(JsonPath.<String>read(cuerpo, "$.datos.ocupacion")).isEqualTo("Maestra");
		assertThat(JsonPath.<Number>read(cuerpo, "$.datos.ingreso_mensual").doubleValue()).isEqualTo(2500.0);
	}

	@Test
	void unTipoInexistenteOInactivoEs400() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long ornato = idDelTipo(admin, "Boleto de ornato");

		assertThat(enviar(HttpMethod.POST, RUTA, ana, cuerpoTramite(999_999L, "{}")).getStatus()).isEqualTo(400);
		enviar(HttpMethod.PUT, "/api/tipos-tramite/" + ornato, admin,
				"{\"nombre\":\"Boleto de ornato\",\"monto\":0,\"activo\":false}");
		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, ana, cuerpoTramite(ornato, "{}"));
		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(JsonPath.<List<String>>read(r.getContentAsString(), "$.errores[*].campo")).contains("tipoId");
	}

	@Test
	void faltaElTipoEs400() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		assertThat(enviar(HttpMethod.POST, RUTA, ana, "{\"datos\":{}}").getStatus()).isEqualTo(400);
	}

	@Test
	void sinTokenEs401() throws Exception {
		assertThat(enviar(HttpMethod.GET, RUTA, null, null).getStatus()).isEqualTo(401);
		assertThat(enviar(HttpMethod.POST, RUTA, null, cuerpoTramite(1, "{}")).getStatus()).isEqualTo(401);
	}

	@ParameterizedTest
	@CsvSource({ "CIUDADANO,201", "GESTOR,403", "AUDITOR,403", "ADMINISTRADOR,201" })
	void soloQuienTienePermisoDeCrearPuedeCrear(String rol, int esperado) throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long ornato = idDelTipo(admin, "Boleto de ornato");
		String token = tokenCon("persona@demo.com", rol);

		assertThat(enviar(HttpMethod.POST, RUTA, token, cuerpoTramite(ornato, "{}")).getStatus()).isEqualTo(esperado);
	}

	@Test
	void unCiudadanoSoloVeSusTramitesYUnoAjenoResponde404() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String luis = tokenCon("luis@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long deAna = crearTramite(ana, ornato, DATOS_ORNATO_VALIDOS);
		long deLuis = crearTramite(luis, ornato, "{}");

		String listaDeLuis = enviar(HttpMethod.GET, RUTA, luis, null).getContentAsString();
		List<Number> idsDeLuis = JsonPath.read(listaDeLuis, "$.content[*].id");

		assertThat(idsDeLuis).extracting(Number::longValue).containsExactly(deLuis);
		assertThat(enviar(HttpMethod.GET, RUTA + "/" + deAna, luis, null).getStatus()).isEqualTo(404);
		assertThat(enviar(HttpMethod.GET, RUTA + "/" + deAna, ana, null).getStatus()).isEqualTo(200);
	}

	@ParameterizedTest
	@CsvSource({ "GESTOR", "AUDITOR", "ADMINISTRADOR" })
	void losRolesConAlcanceTotalVenLosTramitesDeTodos(String rol) throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long deAna = crearTramite(ana, ornato, DATOS_ORNATO_VALIDOS);
		String token = tokenCon("persona@demo.com", rol);

		List<Number> ids = JsonPath.read(enviar(HttpMethod.GET, RUTA, token, null).getContentAsString(),
				"$.content[*].id");

		assertThat(ids).extracting(Number::longValue).contains(deAna);
		assertThat(enviar(HttpMethod.GET, RUTA + "/" + deAna, token, null).getStatus()).isEqualTo(200);
	}

	@Test
	void elPerfilIndicaElAlcanceDelRol() throws Exception {
		String perfilCiudadano = enviar(HttpMethod.GET, "/api/usuarios/yo", tokenCon("ana@demo.com", "CIUDADANO"), null)
				.getContentAsString();
		String perfilGestor = enviar(HttpMethod.GET, "/api/usuarios/yo", tokenCon("gil@demo.com", "GESTOR"), null)
				.getContentAsString();

		assertThat(JsonPath.<Boolean>read(perfilCiudadano, "$.alcanceTotal")).isFalse();
		assertThat(JsonPath.<Boolean>read(perfilGestor, "$.alcanceTotal")).isTrue();
	}

	@Test
	void losDatosSeGuardanComoJsonbEnLaBase() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");

		long id = crearTramite(ana, ornato, DATOS_ORNATO_VALIDOS);

		String dpi = jdbc.queryForObject("SELECT datos->>'dpi' FROM tramite WHERE id = ?", String.class, id);
		assertThat(dpi).isEqualTo("1234567890123");
		String tipoColumna = jdbc.queryForObject(
				"SELECT data_type FROM information_schema.columns WHERE table_name = 'tramite' AND column_name = 'datos'",
				String.class);
		assertThat(tipoColumna).isEqualTo("jsonb");
	}

	@Test
	void editarCambiaLosDatosYElEstadoSeQuedaIgual() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long id = crearTramite(ana, ornato, "{\"dpi\":\"1234567890123\"}");

		MockHttpServletResponse r = enviar(HttpMethod.PUT, RUTA + "/" + id, ana,
				"{\"datos\":{\"ocupacion\":\"Panadera\"}}");

		assertThat(r.getStatus()).isEqualTo(200);
		String cuerpo = r.getContentAsString();
		assertThat(JsonPath.<String>read(cuerpo, "$.estado")).isEqualTo("BORRADOR");
		assertThat(JsonPath.<String>read(cuerpo, "$.datos.ocupacion")).isEqualTo("Panadera");
		assertThat(JsonPath.<Map<String, Object>>read(cuerpo, "$.datos")).doesNotContainKey("dpi");
	}

	@Test
	void editarConDatosInvalidosEs400YNoCambiaNada() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long id = crearTramite(ana, ornato, "{\"dpi\":\"1234567890123\"}");

		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + id, ana, "{\"datos\":{\"dpi\":\"1\"}}").getStatus())
				.isEqualTo(400);

		String dpi = JsonPath.read(enviar(HttpMethod.GET, RUTA + "/" + id, ana, null).getContentAsString(), "$.datos.dpi");
		assertThat(dpi).isEqualTo("1234567890123");
	}

	@Test
	void elCiudadanoNoPuedeEditarElTramiteDeOtro() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String luis = tokenCon("luis@demo.com", "CIUDADANO");
		long deAna = crearTramite(ana, idDelTipo(ana, "Boleto de ornato"), "{}");

		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + deAna, luis, "{\"datos\":{}}").getStatus()).isEqualTo(404);
	}

	@ParameterizedTest
	@CsvSource({ "BORRADOR,200", "OBSERVADO,200", "ENVIADO,409", "EN_REVISION,409", "POR_PAGAR,409",
			"FINALIZADO,409", "RECHAZADO,409" })
	void elCiudadanoSoloEditaEnBorradorYObservado(String estado, int esperado) throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crearTramite(ana, idDelTipo(ana, "Boleto de ornato"), "{}");
		ponerEstado(id, estado);

		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + id, ana, "{\"datos\":{\"ocupacion\":\"Sastre\"}}").getStatus())
				.isEqualTo(esperado);
	}

	@ParameterizedTest
	@CsvSource({ "BORRADOR,200", "ENVIADO,200", "EN_REVISION,200", "OBSERVADO,200", "POR_PAGAR,200",
			"FINALIZADO,409", "RECHAZADO,409" })
	void elGestorEditaEnCualquierEstadoMenosLosFinales(String estado, int esperado) throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crearTramite(ana, idDelTipo(ana, "Boleto de ornato"), "{}");
		String gestor = tokenCon("gil@demo.com", "GESTOR");
		ponerEstado(id, estado);

		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + id, gestor, "{\"datos\":{\"ocupacion\":\"Sastre\"}}")
				.getStatus()).isEqualTo(esperado);
	}

	@Test
	void elAuditorNoPuedeEditar() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crearTramite(ana, idDelTipo(ana, "Boleto de ornato"), "{}");
		String auditor = tokenCon("aud@demo.com", "AUDITOR");

		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + id, auditor, "{\"datos\":{}}").getStatus()).isEqualTo(403);
	}

	@ParameterizedTest
	@CsvSource({ "CIUDADANO,403", "GESTOR,403", "AUDITOR,403", "ADMINISTRADOR,204" })
	void soloElAdministradorPuedeEliminar(String rol, int esperado) throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crearTramite(ana, idDelTipo(ana, "Boleto de ornato"), "{}");
		String token = tokenCon("persona@demo.com", rol);

		assertThat(enviar(HttpMethod.DELETE, RUTA + "/" + id, token, null).getStatus()).isEqualTo(esperado);
	}

	@Test
	void eliminarMarcaElRegistroPeroNoLoBorra() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long id = crearTramite(ana, idDelTipo(ana, "Boleto de ornato"), DATOS_ORNATO_VALIDOS);

		assertThat(enviar(HttpMethod.DELETE, RUTA + "/" + id, admin, null).getStatus()).isEqualTo(204);

		assertThat(enviar(HttpMethod.GET, RUTA + "/" + id, ana, null).getStatus()).isEqualTo(404);
		assertThat(enviar(HttpMethod.GET, RUTA + "/" + id, admin, null).getStatus()).isEqualTo(404);
		List<Number> ids = JsonPath.read(enviar(HttpMethod.GET, RUTA, admin, null).getContentAsString(),
				"$.content[*].id");
		assertThat(ids).extracting(Number::longValue).doesNotContain(id);
		Integer marcadas = jdbc.queryForObject(
				"SELECT count(*) FROM tramite WHERE id = ? AND eliminado_en IS NOT NULL", Integer.class, id);
		assertThat(marcadas).isEqualTo(1);
		assertThat(enviar(HttpMethod.DELETE, RUTA + "/" + id, admin, null).getStatus()).isEqualTo(404);
	}

	@Test
	void elListadoSePaginaYSoloOrdenaPorCamposPermitidos() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		for (int i = 0; i < 3; i++) {
			crearTramite(ana, ornato, "{}");
		}

		String pagina = enviar(HttpMethod.GET, RUTA + "?size=2&page=0&sort=id,asc", ana, null).getContentAsString();

		assertThat(JsonPath.<List<Object>>read(pagina, "$.content")).hasSize(2);
		assertThat(JsonPath.<Integer>read(pagina, "$.page.totalElements")).isEqualTo(3);
		assertThat(enviar(HttpMethod.GET, RUTA + "?sort=datos,asc", ana, null).getStatus()).isEqualTo(400);
		assertThat(enviar(HttpMethod.GET, RUTA + "?sort=ciudadanoId,asc", ana, null).getStatus()).isEqualTo(400);
	}

}
