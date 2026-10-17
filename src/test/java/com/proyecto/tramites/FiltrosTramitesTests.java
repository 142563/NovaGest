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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
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
 * Tarjeta 17: el listado de /api/tramites se filtra por estado, tipo y fechas de creacion, sin saltarse
 * el alcance (un ciudadano solo filtra dentro de lo suyo) ni el permiso tramite:leer.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FiltrosTramitesTests {

	private static final String CLAVE = "clave-segura-123";
	private static final String RUTA = "/api/tramites";
	private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

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

	/** Registra un usuario, le deja solo los roles indicados (puede ser ninguno) y devuelve su token. */
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

	private long idDelTipo(String token, String nombre) throws Exception {
		String cuerpo = enviar(HttpMethod.GET, "/api/tipos-tramite?size=100", token, null).getContentAsString();
		List<Number> ids = JsonPath.read(cuerpo, "$.content[?(@.nombre=='" + nombre + "')].id");
		return ids.get(0).longValue();
	}

	private long crear(String token, long tipoId) throws Exception {
		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, token, "{\"tipoId\":" + tipoId + ",\"datos\":{}}");
		assertThat(r.getStatus()).isEqualTo(201);
		return JsonPath.<Number>read(r.getContentAsString(), "$.id").longValue();
	}

	private void ponerEstado(long id, String estado) {
		em.flush();
		jdbc.update("UPDATE tramite SET estado = ? WHERE id = ?", estado, id);
		em.clear();
	}

	/** Fija la fecha de creacion en hora de Guatemala. */
	private void ponerCreadoEn(long id, LocalDateTime horaGuatemala) {
		em.flush();
		jdbc.update("UPDATE tramite SET creado_en = ? WHERE id = ?",
				Timestamp.from(horaGuatemala.atZone(GUATEMALA).toInstant()), id);
		em.clear();
	}

	private List<Long> ids(String consulta, String token) throws Exception {
		MockHttpServletResponse r = enviar(HttpMethod.GET, RUTA + consulta, token, null);
		assertThat(r.getStatus()).as("estado de " + consulta).isEqualTo(200);
		List<Number> numeros = JsonPath.read(r.getContentAsString(), "$.content[*].id");
		return numeros.stream().map(Number::longValue).toList();
	}

	@Test
	void sinFiltrosElListadoSigueIgual() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long a = crear(ana, ornato);
		long b = crear(ana, ornato);

		assertThat(ids("", ana)).containsExactlyInAnyOrder(a, b);
	}

	@Test
	void filtraPorUnEstado() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long enviado = crear(ana, ornato);
		crear(ana, ornato); // queda en BORRADOR
		ponerEstado(enviado, "ENVIADO");

		assertThat(ids("?estado=ENVIADO", ana)).containsExactly(enviado);
		assertThat(ids("?estado=enviado", ana)).containsExactly(enviado);
	}

	@Test
	void filtraPorVariosEstadosRepetidosOSeparadosPorComas() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long enviado = crear(ana, ornato);
		long rechazado = crear(ana, ornato);
		crear(ana, ornato); // BORRADOR
		ponerEstado(enviado, "ENVIADO");
		ponerEstado(rechazado, "RECHAZADO");

		assertThat(ids("?estado=ENVIADO&estado=RECHAZADO", ana)).containsExactlyInAnyOrder(enviado, rechazado);
		assertThat(ids("?estado=ENVIADO,RECHAZADO", ana)).containsExactlyInAnyOrder(enviado, rechazado);
	}

	@Test
	void filtraPorTipo() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long igss = idDelTipo(ana, "Pago de IGSS");
		long deOrnato = crear(ana, ornato);
		long deIgss = crear(ana, igss);

		assertThat(ids("?tipoId=" + igss, ana)).containsExactly(deIgss);
		assertThat(ids("?tipoId=" + ornato, ana)).containsExactly(deOrnato);
		assertThat(ids("?tipoId=999999", ana)).isEmpty();
	}

	@Test
	void filtraPorFechasIncluyendoElDiaCompletoEnHoraDeGuatemala() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long enero = crear(ana, ornato);
		long febrero = crear(ana, ornato);
		long nocheDelPrimeroDeMarzo = crear(ana, ornato);
		long abril = crear(ana, ornato);
		ponerCreadoEn(enero, LocalDateTime.of(2026, 1, 10, 9, 0));
		ponerCreadoEn(febrero, LocalDateTime.of(2026, 2, 15, 12, 0));
		// 23:30 en Guatemala ya es 2 de marzo en UTC: debe contar como 1 de marzo
		ponerCreadoEn(nocheDelPrimeroDeMarzo, LocalDateTime.of(2026, 3, 1, 23, 30));
		ponerCreadoEn(abril, LocalDateTime.of(2026, 4, 5, 8, 0));

		assertThat(ids("?desde=2026-02-01&hasta=2026-03-01", ana)).containsExactlyInAnyOrder(febrero,
				nocheDelPrimeroDeMarzo);
		assertThat(ids("?hasta=2026-01-31", ana)).containsExactly(enero);
		assertThat(ids("?desde=2026-03-02", ana)).containsExactly(abril);
		assertThat(ids("?desde=2026-02-15&hasta=2026-02-15", ana)).containsExactly(febrero);
	}

	@Test
	void losFiltrosSeCombinan() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long igss = idDelTipo(ana, "Pago de IGSS");
		long buscado = crear(ana, igss);
		long otroTipo = crear(ana, ornato);
		long otroEstado = crear(ana, igss);
		long otraFecha = crear(ana, igss);
		ponerEstado(buscado, "EN_REVISION");
		ponerEstado(otroTipo, "EN_REVISION");
		ponerEstado(otraFecha, "EN_REVISION");
		ponerCreadoEn(buscado, LocalDateTime.of(2026, 5, 20, 10, 0));
		ponerCreadoEn(otroTipo, LocalDateTime.of(2026, 5, 20, 10, 0));
		ponerCreadoEn(otroEstado, LocalDateTime.of(2026, 5, 20, 10, 0));
		ponerCreadoEn(otraFecha, LocalDateTime.of(2026, 1, 1, 10, 0));

		assertThat(ids("?estado=EN_REVISION&tipoId=" + igss + "&desde=2026-05-01", ana)).containsExactly(buscado);
	}

	@Test
	void unCiudadanoSoloFiltraDentroDeSusPropiosTramites() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String luis = tokenCon("luis@demo.com", "CIUDADANO");
		String gestor = tokenCon("gestor@demo.com", "GESTOR");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long deAna = crear(ana, ornato);
		long deLuis = crear(luis, ornato);
		ponerEstado(deAna, "ENVIADO");
		ponerEstado(deLuis, "ENVIADO");

		assertThat(ids("?estado=ENVIADO", ana)).containsExactly(deAna);
		assertThat(ids("?estado=ENVIADO", luis)).containsExactly(deLuis);
		assertThat(ids("?estado=ENVIADO", gestor)).contains(deAna, deLuis);
	}

	@Test
	void losEliminadosNoAparecenAunqueCoincidanConElFiltro() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		long borrado = crear(ana, ornato);
		long vivo = crear(ana, ornato);
		assertThat(enviar(HttpMethod.DELETE, RUTA + "/" + borrado, admin, null).getStatus()).isEqualTo(204);

		assertThat(ids("?estado=BORRADOR&tipoId=" + ornato, ana)).containsExactly(vivo);
	}

	@Test
	void elTotalDeLaPaginaCuentaSoloLoFiltrado() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long ornato = idDelTipo(ana, "Boleto de ornato");
		for (int i = 0; i < 3; i++) {
			ponerEstado(crear(ana, ornato), "ENVIADO");
		}
		crear(ana, ornato);

		String cuerpo = enviar(HttpMethod.GET, RUTA + "?estado=ENVIADO&size=2", ana, null).getContentAsString();

		assertThat(JsonPath.<Integer>read(cuerpo, "$.page.totalElements")).isEqualTo(3);
		assertThat(JsonPath.<List<Object>>read(cuerpo, "$.content")).hasSize(2);
	}

	@Test
	void filtrosInvalidosSon400ConElCampoYSinRepetirElValor() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		MockHttpServletResponse r = enviar(HttpMethod.GET,
				RUTA + "?estado=INVENTADO-XYZ&tipoId=abc-123&desde=2026-13-45&hasta=ayer", ana, null);

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(r.getContentType()).startsWith("application/problem+json");
		String cuerpo = r.getContentAsString(StandardCharsets.UTF_8);
		assertThat(JsonPath.<List<String>>read(cuerpo, "$.errores[*].campo")).contains("estado", "tipoId", "desde",
				"hasta");
		assertThat(cuerpo).doesNotContain("INVENTADO-XYZ").doesNotContain("abc-123").doesNotContain("2026-13-45")
				.doesNotContain("ayer");
	}

	@Test
	void desdeDespuesDeHastaEs400() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		MockHttpServletResponse r = enviar(HttpMethod.GET, RUTA + "?desde=2026-06-01&hasta=2026-05-01", ana, null);

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(JsonPath.<List<String>>read(r.getContentAsString(), "$.errores[*].campo")).contains("desde");
	}

	@Test
	void sinPermisoDeLecturaEs403AunqueVengaConFiltros() throws Exception {
		String sinRoles = tokenCon("sinroles@demo.com");

		assertThat(enviar(HttpMethod.GET, RUTA + "?estado=ENVIADO", sinRoles, null).getStatus()).isEqualTo(403);
	}

	@Test
	void sinTokenEs401AunqueVengaConFiltros() throws Exception {
		assertThat(enviar(HttpMethod.GET, RUTA + "?estado=ENVIADO", null, null).getStatus()).isEqualTo(401);
	}

}
