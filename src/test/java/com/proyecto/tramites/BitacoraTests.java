package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.jayway.jsonpath.JsonPath;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.RolRepository;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** Tarjeta 23: cada creacion, edicion y eliminacion queda en la bitacora, y se consulta solo por GET. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BitacoraTests {

	private static final String CLAVE = "clave-segura-123";
	private static final String RUTA = "/api/bitacora";
	private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private RolRepository roles;

	@Autowired
	private JdbcTemplate jdbc;

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

	private long idUsuario(String token) throws Exception {
		return JsonPath.<Number>read(enviar(HttpMethod.GET, "/api/usuarios/yo", token, null).getContentAsString(), "$.id")
				.longValue();
	}

	private long idDelTipo(String token, String nombre) throws Exception {
		String cuerpo = enviar(HttpMethod.GET, "/api/tipos-tramite?size=100", token, null).getContentAsString();
		return JsonPath.<List<Number>>read(cuerpo, "$.content[?(@.nombre=='" + nombre + "')].id").get(0).longValue();
	}

	private long crearTramite(String token, String datos) throws Exception {
		long ornato = idDelTipo(token, "Boleto de ornato");
		MockHttpServletResponse r = enviar(HttpMethod.POST, "/api/tramites", token,
				"{\"tipoId\":" + ornato + ",\"datos\":" + datos + "}");
		assertThat(r.getStatus()).isEqualTo(201);
		return JsonPath.<Number>read(r.getContentAsString(), "$.id").longValue();
	}

	private MockHttpServletResponse consultar(String token, String parametros) throws Exception {
		return enviar(HttpMethod.GET, RUTA + (parametros == null ? "" : "?" + parametros), token, null);
	}

	private List<String> acciones(MockHttpServletResponse respuesta) throws Exception {
		return JsonPath.read(respuesta.getContentAsString(), "$.content[*].accion");
	}

	private int filas(String filtroSql) {
		return jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE " + filtroSql, Integer.class);
	}

	// ---------- Cada accion deja su fila ----------

	@Test
	void registrarseDejaConstanciaConElIdDelUsuarioYSinSuCorreo() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long idAna = idUsuario(ana);

		Map<String, Object> fila = jdbc.queryForMap(
				"SELECT actor_id, recurso, recurso_id, antes::text AS antes, despues::text AS despues FROM bitacora "
						+ "WHERE accion = 'REGISTRO_USUARIO'");

		assertThat(((Number) fila.get("actor_id")).longValue()).isEqualTo(idAna);
		assertThat(fila.get("recurso")).isEqualTo("usuario");
		assertThat(((Number) fila.get("recurso_id")).longValue()).isEqualTo(idAna);
		assertThat(String.valueOf(fila.get("despues"))).doesNotContain("ana@demo.com");
	}

	@Test
	void crearEditarYEliminarUnTramiteQuedanRegistrados() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long idAna = idUsuario(ana);
		long idAdmin = idUsuario(admin);

		long tramite = crearTramite(ana, "{\"dpi\":\"1234567890123\",\"ocupacion\":\"Comerciante\"}");
		enviar(HttpMethod.PUT, "/api/tramites/" + tramite, ana,
				"{\"datos\":{\"dpi\":\"1234567890123\",\"ocupacion\":\"Panadera\"}}");
		enviar(HttpMethod.DELETE, "/api/tramites/" + tramite, admin, null);

		List<Map<String, Object>> filas = jdbc.queryForList(
				"SELECT accion, actor_id, recurso, recurso_id FROM bitacora WHERE recurso = 'tramite' ORDER BY id");
		assertThat(filas).extracting(f -> f.get("accion"))
				.containsExactly("CREAR_TRAMITE", "EDITAR_TRAMITE", "ELIMINAR_TRAMITE");
		assertThat(filas).extracting(f -> ((Number) f.get("recurso_id")).longValue()).containsOnly(tramite);
		assertThat(filas).extracting(f -> ((Number) f.get("actor_id")).longValue()).containsExactly(idAna, idAna, idAdmin);
	}

	@Test
	void editarUnTramiteGuardaSoloLosNombresDeLosCamposQueCambiaron() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long tramite = crearTramite(ana, "{\"dpi\":\"1234567890123\",\"ocupacion\":\"Comerciante\",\"ingreso_mensual\":2500}");

		enviar(HttpMethod.PUT, "/api/tramites/" + tramite, ana,
				"{\"datos\":{\"dpi\":\"1234567890123\",\"ocupacion\":\"Panadera\",\"ingreso_mensual\":2500.00}}");

		String despues = jdbc.queryForObject(
				"SELECT despues::text FROM bitacora WHERE accion = 'EDITAR_TRAMITE'", String.class);
		assertThat(JsonPath.<List<String>>read(despues, "$.camposModificados")).containsExactly("ocupacion");
	}

	@Test
	void unaEdicionSinCambiosNoDiceQueSeModificoNada() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long tramite = crearTramite(ana, "{\"dpi\":\"1234567890123\",\"ingreso_mensual\":2500}");

		enviar(HttpMethod.PUT, "/api/tramites/" + tramite, ana,
				"{\"datos\":{\"dpi\":\"1234567890123\",\"ingreso_mensual\":2500.0}}");

		String despues = jdbc.queryForObject(
				"SELECT despues::text FROM bitacora WHERE accion = 'EDITAR_TRAMITE'", String.class);
		assertThat(JsonPath.<List<String>>read(despues, "$.camposModificados")).isEmpty();
	}

	@Test
	void crearEditarYEliminarUnTipoDeTramiteQuedanRegistrados() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long idAdmin = idUsuario(admin);

		long id = JsonPath.<Number>read(enviar(HttpMethod.POST, "/api/tipos-tramite", admin,
				"{\"nombre\":\"Licencia\",\"monto\":50}").getContentAsString(), "$.id").longValue();
		enviar(HttpMethod.PUT, "/api/tipos-tramite/" + id, admin, "{\"nombre\":\"Licencia mayor\",\"monto\":75}");
		enviar(HttpMethod.DELETE, "/api/tipos-tramite/" + id, admin, null);

		List<Map<String, Object>> filas = jdbc.queryForList(
				"SELECT accion, actor_id, recurso_id, antes::text AS antes, despues::text AS despues "
						+ "FROM bitacora WHERE recurso = 'tipo_tramite' ORDER BY id");
		assertThat(filas).extracting(f -> f.get("accion"))
				.containsExactly("CREAR_TIPO_TRAMITE", "EDITAR_TIPO_TRAMITE", "ELIMINAR_TIPO_TRAMITE");
		assertThat(filas).extracting(f -> ((Number) f.get("actor_id")).longValue()).containsOnly(idAdmin);
		assertThat(filas).extracting(f -> ((Number) f.get("recurso_id")).longValue()).containsOnly(id);
		Map<String, Object> edicion = filas.get(1);
		assertThat(JsonPath.<String>read(String.valueOf(edicion.get("antes")), "$.nombre")).isEqualTo("Licencia");
		assertThat(JsonPath.<String>read(String.valueOf(edicion.get("despues")), "$.nombre")).isEqualTo("Licencia mayor");
		assertThat(JsonPath.<Number>read(String.valueOf(edicion.get("despues")), "$.monto").doubleValue()).isEqualTo(75.0);
	}

	@Test
	void unaAccionQueFallaNoDejaFilaEnLaBitacora() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		int antes = filas("accion = 'CREAR_TIPO_TRAMITE'");

		assertThat(enviar(HttpMethod.POST, "/api/tipos-tramite", admin, "{\"nombre\":\"Boleto de ornato\",\"monto\":1}")
				.getStatus()).isEqualTo(409);
		assertThat(enviar(HttpMethod.POST, "/api/tipos-tramite", admin, "{\"nombre\":\"\",\"monto\":1}").getStatus())
				.isEqualTo(400);

		assertThat(filas("accion = 'CREAR_TIPO_TRAMITE'")).isEqualTo(antes);
	}

	@Test
	void laBitacoraNuncaGuardaDatosPersonalesNiClaves() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long tramite = crearTramite(ana, "{\"dpi\":\"9876543210123\",\"ocupacion\":\"Ocupacion-Secreta\",\"ingreso_mensual\":7777.77}");
		enviar(HttpMethod.PUT, "/api/tramites/" + tramite, ana,
				"{\"datos\":{\"dpi\":\"9876543210123\",\"ocupacion\":\"Otra-Ocupacion-Secreta\"}}");
		enviar(HttpMethod.DELETE, "/api/tramites/" + tramite, admin, null);

		String todo = String.join(" ", jdbc.queryForList(
				"SELECT accion || ' ' || coalesce(antes::text,'') || ' ' || coalesce(despues::text,'') FROM bitacora",
				String.class));

		assertThat(todo).doesNotContain("9876543210123").doesNotContain("Ocupacion-Secreta")
				.doesNotContain("7777.77").doesNotContain("ana@demo.com").doesNotContain(CLAVE).doesNotContain("$2");
	}

	@Test
	void cadaAccionRegistraLaDireccionDesdeDondeSeHizo() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		crearTramite(ana, "{}");

		List<String> ips = jdbc.queryForList("SELECT ip FROM bitacora WHERE accion = 'CREAR_TRAMITE'", String.class);

		assertThat(ips).containsExactly("127.0.0.1");
	}

	// ---------- Consulta ----------

	@ParameterizedTest
	@CsvSource({ "AUDITOR,200", "ADMINISTRADOR,200", "CIUDADANO,403", "GESTOR,403" })
	void soloElAuditorYElAdministradorPuedenConsultarLaBitacora(String rol, int esperado) throws Exception {
		String token = tokenCon("persona@demo.com", rol);

		assertThat(consultar(token, null).getStatus()).isEqualTo(esperado);
	}

	@Test
	void sinTokenLaConsultaEs401() throws Exception {
		assertThat(consultar(null, null).getStatus()).isEqualTo(401);
	}

	@Test
	void laRespuestaTraeQuienQueYCuandoConElCorreoEnmascarado() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String auditor = tokenCon("aud@demo.com", "AUDITOR");
		long tramite = crearTramite(ana, "{}");

		MockHttpServletResponse r = consultar(auditor, "accion=CREAR_TRAMITE&recursoId=" + tramite);

		String cuerpo = r.getContentAsString();
		assertThat(JsonPath.<List<Object>>read(cuerpo, "$.content")).hasSize(1);
		assertThat(JsonPath.<Number>read(cuerpo, "$.content[0].actorId").longValue()).isEqualTo(idUsuario(ana));
		assertThat(JsonPath.<String>read(cuerpo, "$.content[0].actorCorreo")).isEqualTo("a***@demo.com");
		assertThat(JsonPath.<String>read(cuerpo, "$.content[0].recurso")).isEqualTo("tramite");
		assertThat(JsonPath.<String>read(cuerpo, "$.content[0].despues.estado")).isEqualTo("BORRADOR");
		assertThat(JsonPath.<String>read(cuerpo, "$.content[0].ip")).isEqualTo("127.0.0.1");
		assertThat(JsonPath.<String>read(cuerpo, "$.content[0].fecha")).isNotBlank();
		assertThat(cuerpo).doesNotContain("ana@demo.com");
	}

	@Test
	void seFiltraPorRecursoRecursoIdAccionYActor() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		String auditor = tokenCon("aud@demo.com", "AUDITOR");
		long tramite = crearTramite(ana, "{}");
		enviar(HttpMethod.POST, "/api/tipos-tramite", admin, "{\"nombre\":\"Otro tipo\",\"monto\":1}");

		assertThat(acciones(consultar(auditor, "recurso=tipo_tramite"))).containsOnly("CREAR_TIPO_TRAMITE");
		assertThat(acciones(consultar(auditor, "recurso=tramite&recursoId=" + tramite))).containsExactly("CREAR_TRAMITE");
		assertThat(acciones(consultar(auditor, "accion=REGISTRO_USUARIO"))).containsOnly("REGISTRO_USUARIO").hasSize(3);
		List<String> deAdmin = acciones(consultar(auditor, "actorId=" + idUsuario(admin) + "&recurso=tipo_tramite"));
		assertThat(deAdmin).containsExactly("CREAR_TIPO_TRAMITE");
		assertThat(acciones(consultar(auditor, "accion=NO_EXISTE"))).isEmpty();
	}

	@Test
	void seFiltraPorRangoDeFechasEnHoraDeGuatemala() throws Exception {
		String auditor = tokenCon("aud@demo.com", "AUDITOR");
		LocalDate hoy = LocalDate.now(GUATEMALA);

		assertThat(acciones(consultar(auditor, "desde=" + hoy.minusDays(1) + "&hasta=" + hoy.plusDays(1)))).isNotEmpty();
		assertThat(acciones(consultar(auditor, "desde=" + hoy + "&hasta=" + hoy))).isNotEmpty();
		assertThat(acciones(consultar(auditor, "desde=" + hoy.plusDays(1)))).isEmpty();
		assertThat(acciones(consultar(auditor, "hasta=" + hoy.minusDays(1)))).isEmpty();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"desde=ayer|desde", "hasta=2026-13-45|hasta", "desde=2026-10-10&hasta=2026-10-01|desde" })
	void losFiltrosInvalidosSon400ConElCampo(String parametros, String campo) throws Exception {
		String auditor = tokenCon("aud@demo.com", "AUDITOR");

		MockHttpServletResponse r = consultar(auditor, parametros);

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(JsonPath.<List<String>>read(r.getContentAsString(), "$.errores[*].campo")).contains(campo);
	}

	@Test
	void seRechazanFiltrosAbusivosYOrdenesNoPermitidos() throws Exception {
		String auditor = tokenCon("aud@demo.com", "AUDITOR");

		assertThat(consultar(auditor, "accion=" + "x".repeat(60)).getStatus()).isEqualTo(400);
		assertThat(consultar(auditor, "sort=despues,asc").getStatus()).isEqualTo(400);
		assertThat(consultar(auditor, "sort=ip,asc").getStatus()).isEqualTo(400);
		assertThat(consultar(auditor, "recursoId=abc").getStatus()).isEqualTo(400);
		assertThat(JsonPath.<Integer>read(consultar(auditor, "size=1000").getContentAsString(), "$.page.size"))
				.isLessThanOrEqualTo(100);
	}

	@Test
	void porDefectoVieneLoMasRecientePrimeroYSePagina() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String auditor = tokenCon("aud@demo.com", "AUDITOR");
		for (int i = 0; i < 3; i++) {
			crearTramite(ana, "{}");
		}

		String cuerpo = consultar(auditor, "recurso=tramite&size=2&page=0").getContentAsString();

		List<Number> ids = JsonPath.read(cuerpo, "$.content[*].id");
		assertThat(ids).hasSize(2);
		assertThat(ids.get(0).longValue()).isGreaterThan(ids.get(1).longValue());
		assertThat(JsonPath.<Integer>read(cuerpo, "$.page.totalElements")).isEqualTo(3);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.page.totalPages")).isEqualTo(2);
	}

	@Test
	void consultarLaBitacoraTambienQuedaRegistradoYNoApareceEnSuPropiaRespuesta() throws Exception {
		String auditor = tokenCon("aud@demo.com", "AUDITOR");
		long idAuditor = idUsuario(auditor);

		MockHttpServletResponse primera = consultar(auditor, "recurso=tramite");
		assertThat(acciones(primera)).doesNotContain("CONSULTA_BITACORA");

		MockHttpServletResponse segunda = consultar(auditor, "accion=CONSULTA_BITACORA");
		String cuerpo = segunda.getContentAsString();
		assertThat(JsonPath.<List<Object>>read(cuerpo, "$.content")).hasSize(1);
		assertThat(JsonPath.<Number>read(cuerpo, "$.content[0].actorId").longValue()).isEqualTo(idAuditor);
		assertThat(JsonPath.<String>read(cuerpo, "$.content[0].despues.recurso")).isEqualTo("tramite");
	}

	// ---------- Solo lectura ----------

	@Test
	void porLaApiNadieCreaEditaNiBorraLaBitacora() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		int antes = filas("true");

		for (HttpMethod metodo : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
			for (String ruta : List.of(RUTA, RUTA + "/1")) {
				int estado = enviar(metodo, ruta, admin, "{}").getStatus();
				assertThat(estado).as(metodo + " " + ruta).isIn(403, 404, 405);
			}
		}

		assertThat(filas("true")).isEqualTo(antes);
	}

}
