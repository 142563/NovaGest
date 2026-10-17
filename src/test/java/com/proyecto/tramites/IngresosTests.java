package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.jayway.jsonpath.JsonPath;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.RolRepository;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/**
 * Tarjeta 19: CRUD de ingresos propios. Dato personal: solo el dueno lo ve y lo toca (ajeno = 404), ningun
 * otro rol tiene permiso (403), el borrado es logico y cada escritura queda en la bitacora sin el monto.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IngresosTests {

	private static final String CLAVE = "clave-segura-123";
	private static final String RUTA = "/api/ingresos";

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

	/** Registra un usuario, le deja solo los roles indicados y devuelve su token. */
	private String tokenCon(String correo, String... rolesDelUsuario) throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(
				"{\"nombre\":\"Persona Prueba\",\"correo\":\"" + correo + "\",\"clave\":\"" + CLAVE + "\"}"));
		Usuario usuario = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull(correo).orElseThrow();
		usuario.getRoles().clear();
		for (String rol : rolesDelUsuario) {
			usuario.getRoles().add(roles.findByNombre(rol).orElseThrow());
		}
		usuarios.saveAndFlush(usuario);
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"clave\":\"" + CLAVE + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(cuerpo, "$.accessToken");
	}

	private static String cuerpo(String fecha, String monto, String descripcion) {
		String desc = descripcion == null ? "" : ",\"descripcion\":\"" + descripcion + "\"";
		return "{\"fecha\":\"" + fecha + "\",\"montoGtq\":" + monto + desc + "}";
	}

	private long crear(String token, String fecha, String monto) throws Exception {
		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, token, cuerpo(fecha, monto, "Salario"));
		assertThat(r.getStatus()).isEqualTo(201);
		return JsonPath.<Number>read(r.getContentAsString(), "$.id").longValue();
	}

	private List<Long> ids(String consulta, String token) throws Exception {
		MockHttpServletResponse r = enviar(HttpMethod.GET, RUTA + consulta, token, null);
		assertThat(r.getStatus()).as("estado de " + consulta).isEqualTo(200);
		List<Number> numeros = JsonPath.read(r.getContentAsString(), "$.content[*].id");
		return numeros.stream().map(Number::longValue).toList();
	}

	private List<Map<String, Object>> bitacoraDe(long idIngreso) {
		em.flush();
		return jdbc.queryForList("SELECT accion, antes::text AS antes, despues::text AS despues FROM bitacora "
				+ "WHERE recurso = 'ingreso' AND recurso_id = ? ORDER BY id", idIngreso);
	}

	private static String hoy() {
		return LocalDate.now(ZoneId.of("America/Guatemala")).toString();
	}

	// ---------------------------------------------------------------- caso feliz

	@Test
	void unCiudadanoRegistraUnIngresoYLoConsulta() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, ana, cuerpo("2026-09-30", "3500.50", "  Salario  "));

		assertThat(r.getStatus()).isEqualTo(201);
		String json = r.getContentAsString();
		long id = JsonPath.<Number>read(json, "$.id").longValue();
		assertThat(JsonPath.<String>read(json, "$.fecha")).isEqualTo("2026-09-30");
		assertThat(JsonPath.<Number>read(json, "$.montoGtq").doubleValue()).isEqualTo(3500.50);
		assertThat(JsonPath.<String>read(json, "$.descripcion")).isEqualTo("Salario");
		assertThat(json).doesNotContain("usuarioId").doesNotContain("eliminadoEn");

		MockHttpServletResponse leido = enviar(HttpMethod.GET, RUTA + "/" + id, ana, null);
		assertThat(leido.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<String>read(leido.getContentAsString(), "$.fecha")).isEqualTo("2026-09-30");
	}

	@Test
	void laFechaDeHoyEsValida() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		assertThat(enviar(HttpMethod.POST, RUTA, ana, cuerpo(hoy(), "100", null)).getStatus()).isEqualTo(201);
	}

	@Test
	void unCiudadanoEditaSuIngreso() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crear(ana, "2026-09-30", "3500");

		MockHttpServletResponse r = enviar(HttpMethod.PUT, RUTA + "/" + id, ana, cuerpo("2026-09-29", "4000", "Bono"));

		assertThat(r.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<String>read(r.getContentAsString(), "$.fecha")).isEqualTo("2026-09-29");
		assertThat(JsonPath.<Number>read(r.getContentAsString(), "$.montoGtq").doubleValue()).isEqualTo(4000.0);
		assertThat(JsonPath.<String>read(r.getContentAsString(), "$.descripcion")).isEqualTo("Bono");
	}

	@Test
	void eliminarEsLogicoYElIngresoDesapareceDeLaApi() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crear(ana, "2026-09-30", "3500");

		assertThat(enviar(HttpMethod.DELETE, RUTA + "/" + id, ana, null).getStatus()).isEqualTo(204);

		assertThat(enviar(HttpMethod.GET, RUTA + "/" + id, ana, null).getStatus()).isEqualTo(404);
		assertThat(ids("", ana)).doesNotContain(id);
		em.flush();
		Integer marcados = jdbc.queryForObject(
				"SELECT count(*) FROM ingreso WHERE id = ? AND eliminado_en IS NOT NULL", Integer.class, id);
		assertThat(marcados).isEqualTo(1);
	}

	@Test
	void elListadoSoloTraeLosPropiosOrdenadosPorFechaYFiltraPorRango() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String luis = tokenCon("luis@demo.com", "CIUDADANO");
		long agosto = crear(ana, "2026-08-15", "1000");
		long septiembre = crear(ana, "2026-09-15", "2000");
		long octubre = crear(ana, "2026-10-01", "3000");
		long deLuis = crear(luis, "2026-09-15", "9999");

		assertThat(ids("", ana)).containsExactly(octubre, septiembre, agosto);
		assertThat(ids("", luis)).containsExactly(deLuis);
		assertThat(ids("?desde=2026-09-01&hasta=2026-09-30", ana)).containsExactly(septiembre);
		assertThat(ids("?desde=2026-09-15", ana)).containsExactly(octubre, septiembre);
		assertThat(ids("?hasta=2026-09-15", ana)).containsExactly(septiembre, agosto);
	}

	// ---------------------------------------------------------------- propiedad del dato y permisos

	@Test
	void elIngresoDeOtroCiudadanoResponde404EnTodasLasOperaciones() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String luis = tokenCon("luis@demo.com", "CIUDADANO");
		long deAna = crear(ana, "2026-09-30", "3500");

		assertThat(enviar(HttpMethod.GET, RUTA + "/" + deAna, luis, null).getStatus()).isEqualTo(404);
		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + deAna, luis, cuerpo("2026-09-30", "1", null)).getStatus())
				.isEqualTo(404);
		assertThat(enviar(HttpMethod.DELETE, RUTA + "/" + deAna, luis, null).getStatus()).isEqualTo(404);

		String json = enviar(HttpMethod.GET, RUTA + "/" + deAna, ana, null).getContentAsString();
		assertThat(JsonPath.<Number>read(json, "$.montoGtq").doubleValue()).isEqualTo(3500.0);
	}

	@ParameterizedTest
	@ValueSource(strings = { "GESTOR", "AUDITOR", "ADMINISTRADOR" })
	void ningunOtroRolPuedeVerNiRegistrarIngresos(String rol) throws Exception {
		String token = tokenCon("otro@demo.com", rol);

		assertThat(enviar(HttpMethod.GET, RUTA, token, null).getStatus()).isEqualTo(403);
		assertThat(enviar(HttpMethod.GET, RUTA + "/1", token, null).getStatus()).isEqualTo(403);
		assertThat(enviar(HttpMethod.POST, RUTA, token, cuerpo("2026-09-30", "100", null)).getStatus())
				.isEqualTo(403);
		assertThat(enviar(HttpMethod.PUT, RUTA + "/1", token, cuerpo("2026-09-30", "100", null)).getStatus())
				.isEqualTo(403);
		assertThat(enviar(HttpMethod.DELETE, RUTA + "/1", token, null).getStatus()).isEqualTo(403);
	}

	@Test
	void unAdministradorNoVeLosIngresosDeUnCiudadanoAunqueTengaAlcanceTotal() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long deAna = crear(ana, "2026-09-30", "3500");

		MockHttpServletResponse r = enviar(HttpMethod.GET, RUTA + "/" + deAna, admin, null);

		assertThat(r.getStatus()).isEqualTo(403);
		assertThat(r.getContentAsString()).doesNotContain("3500");
	}

	@Test
	void sinTokenEs401() throws Exception {
		assertThat(enviar(HttpMethod.GET, RUTA, null, null).getStatus()).isEqualTo(401);
		assertThat(enviar(HttpMethod.POST, RUTA, null, cuerpo("2026-09-30", "100", null)).getStatus()).isEqualTo(401);
	}

	// ---------------------------------------------------------------- validaciones

	@ParameterizedTest
	@ValueSource(strings = { "0", "-5", "10.123", "1234567890123" })
	void unMontoInvalidoEs400(String monto) throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, ana, cuerpo("2026-09-30", monto, null));

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(r.getContentType()).startsWith("application/problem+json");
	}

	@Test
	void faltanFechaYMontoEs400() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		assertThat(enviar(HttpMethod.POST, RUTA, ana, "{\"descripcion\":\"x\"}").getStatus()).isEqualTo(400);
	}

	@Test
	void unaFechaFuturaEs400ConElCampo() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String manana = LocalDate.now(ZoneId.of("America/Guatemala")).plusDays(1).toString();

		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, ana, cuerpo(manana, "100", null));

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(JsonPath.<List<String>>read(r.getContentAsString(), "$.errores[*].campo")).contains("fecha");
	}

	@Test
	void unaDescripcionDeMasDe200CaracteresEs400() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		assertThat(enviar(HttpMethod.POST, RUTA, ana, cuerpo("2026-09-30", "100", "x".repeat(201))).getStatus())
				.isEqualTo(400);
	}

	@Test
	void filtrosDeFechaInvalidosSon400SinRepetirElValor() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		MockHttpServletResponse r = enviar(HttpMethod.GET, RUTA + "?desde=ayer-XYZ&hasta=2026-13-45", ana, null);

		assertThat(r.getStatus()).isEqualTo(400);
		String cuerpo = r.getContentAsString(StandardCharsets.UTF_8);
		assertThat(JsonPath.<List<String>>read(cuerpo, "$.errores[*].campo")).contains("desde", "hasta");
		assertThat(cuerpo).doesNotContain("ayer-XYZ").doesNotContain("2026-13-45");
	}

	@Test
	void desdeDespuesDeHastaEs400() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		assertThat(enviar(HttpMethod.GET, RUTA + "?desde=2026-10-01&hasta=2026-09-01", ana, null).getStatus())
				.isEqualTo(400);
	}

	@Test
	void ordenarPorUnCampoNoPermitidoEs400() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		assertThat(enviar(HttpMethod.GET, RUTA + "?sort=usuarioId", ana, null).getStatus()).isEqualTo(400);
	}

	// ---------------------------------------------------------------- bitacora

	@Test
	void crearEditarYEliminarQuedanEnLaBitacoraSinElMontoNiLaDescripcion() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		MockHttpServletResponse creado = enviar(HttpMethod.POST, RUTA, ana,
				cuerpo("2026-09-30", "3517.25", "Venta secreta"));
		long id = JsonPath.<Number>read(creado.getContentAsString(), "$.id").longValue();
		enviar(HttpMethod.PUT, RUTA + "/" + id, ana, cuerpo("2026-09-30", "4123.75", "Venta secreta"));
		enviar(HttpMethod.DELETE, RUTA + "/" + id, ana, null);

		List<Map<String, Object>> filas = bitacoraDe(id);

		assertThat(filas).extracting(f -> f.get("accion")).containsExactly("CREAR_INGRESO", "EDITAR_INGRESO",
				"ELIMINAR_INGRESO");
		assertThat(String.valueOf(filas.get(1).get("despues"))).contains("montoGtq").doesNotContain("descripcion");
		for (Map<String, Object> fila : filas) {
			String texto = fila.get("antes") + " " + fila.get("despues");
			assertThat(texto).doesNotContain("3517").doesNotContain("4123").doesNotContain("Venta secreta");
		}
	}

	@Test
	void unaOperacionRechazadaNoDejaRastroEnLaBitacora() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String luis = tokenCon("luis@demo.com", "CIUDADANO");
		long deAna = crear(ana, "2026-09-30", "3500");

		enviar(HttpMethod.DELETE, RUTA + "/" + deAna, luis, null);

		assertThat(bitacoraDe(deAna)).extracting(f -> f.get("accion")).containsExactly("CREAR_INGRESO");
	}

}
