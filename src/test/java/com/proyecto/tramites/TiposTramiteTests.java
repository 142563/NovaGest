package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.jayway.jsonpath.JsonPath;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.RolRepository;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/** Tarjeta 14: CRUD de tipos de tramite con permisos por rol y borrado logico. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TiposTramiteTests {

	private static final String CLAVE = "clave-segura-123";
	private static final String RUTA = "/api/tipos-tramite";

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

	private static String tipo(String nombre, String monto) {
		return "{\"nombre\":\"" + nombre + "\",\"descripcion\":\"Descripcion\",\"monto\":" + monto + "}";
	}

	private long crear(String tokenAdmin, String nombre, String monto) throws Exception {
		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, tokenAdmin, tipo(nombre, monto));
		assertThat(r.getStatus()).isEqualTo(201);
		return JsonPath.<Number>read(r.getContentAsString(), "$.id").longValue();
	}

	@Test
	void ornatoEIgssExistenComoTipos() throws Exception {
		MockHttpServletResponse r = enviar(HttpMethod.GET, RUTA, tokenCon("ana@demo.com", "CIUDADANO"), null);

		assertThat(r.getStatus()).isEqualTo(200);
		List<String> nombres = JsonPath.read(r.getContentAsString(), "$.content[*].nombre");
		assertThat(nombres).contains("Boleto de ornato", "Pago de IGSS");
	}

	@Test
	void sinTokenEs401() throws Exception {
		assertThat(enviar(HttpMethod.GET, RUTA, null, null).getStatus()).isEqualTo(401);
		assertThat(enviar(HttpMethod.POST, RUTA, null, tipo("Nuevo", "10")).getStatus()).isEqualTo(401);
	}

	@ParameterizedTest
	@ValueSource(strings = { "CIUDADANO", "GESTOR", "AUDITOR", "ADMINISTRADOR" })
	void todosLosRolesPuedenLeerLosTipos(String rol) throws Exception {
		String token = tokenCon("persona@demo.com", rol);

		assertThat(enviar(HttpMethod.GET, RUTA, token, null).getStatus()).isEqualTo(200);
	}

	@ParameterizedTest
	@CsvSource({ "CIUDADANO,403", "GESTOR,403", "AUDITOR,403", "ADMINISTRADOR,201" })
	void soloElAdministradorPuedeCrear(String rol, int esperado) throws Exception {
		String token = tokenCon("persona@demo.com", rol);

		assertThat(enviar(HttpMethod.POST, RUTA, token, tipo("Licencia de construccion", "150.00")).getStatus())
				.isEqualTo(esperado);
	}

	@ParameterizedTest
	@CsvSource({ "CIUDADANO,403", "GESTOR,403", "AUDITOR,403", "ADMINISTRADOR,200" })
	void soloElAdministradorPuedeEditar(String rol, int esperado) throws Exception {
		long id = crear(tokenCon("admin@demo.com", "ADMINISTRADOR"), "Para editar", "10");
		String token = tokenCon("persona@demo.com", rol);

		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + id, token, tipo("Editado " + rol, "20")).getStatus())
				.isEqualTo(esperado);
	}

	@ParameterizedTest
	@CsvSource({ "CIUDADANO,403", "GESTOR,403", "AUDITOR,403", "ADMINISTRADOR,204" })
	void soloElAdministradorPuedeEliminar(String rol, int esperado) throws Exception {
		long id = crear(tokenCon("admin@demo.com", "ADMINISTRADOR"), "Para borrar", "10");
		String token = tokenCon("persona@demo.com", rol);

		assertThat(enviar(HttpMethod.DELETE, RUTA + "/" + id, token, null).getStatus()).isEqualTo(esperado);
	}

	@Test
	void elAdministradorCreaYLeeUnTipo() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");

		long id = crear(admin, "Certificado de residencia", "25.50");

		MockHttpServletResponse r = enviar(HttpMethod.GET, RUTA + "/" + id, admin, null);
		assertThat(r.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<String>read(r.getContentAsString(), "$.nombre")).isEqualTo("Certificado de residencia");
		assertThat(JsonPath.<Number>read(r.getContentAsString(), "$.monto").doubleValue()).isEqualTo(25.5);
		assertThat(JsonPath.<Boolean>read(r.getContentAsString(), "$.activo")).isTrue();
	}

	@Test
	void unNombreRepetidoSinImportarMayusculasEs409() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");

		assertThat(enviar(HttpMethod.POST, RUTA, admin, tipo("BOLETO DE ORNATO", "5")).getStatus()).isEqualTo(409);
	}

	@Test
	void losDatosInvalidosSon400() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");

		assertThat(enviar(HttpMethod.POST, RUTA, admin, tipo("", "10")).getStatus()).isEqualTo(400);
		assertThat(enviar(HttpMethod.POST, RUTA, admin, tipo("Monto negativo", "-1")).getStatus()).isEqualTo(400);
		assertThat(enviar(HttpMethod.POST, RUTA, admin, tipo("Tres decimales", "1.234")).getStatus()).isEqualTo(400);
		assertThat(enviar(HttpMethod.POST, RUTA, admin, "{\"nombre\":\"Sin monto\"}").getStatus()).isEqualTo(400);
	}

	@Test
	void editarCambiaLosDatosYUnIdInexistenteEs404() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long id = crear(admin, "Original", "10");

		MockHttpServletResponse r = enviar(HttpMethod.PUT, RUTA + "/" + id, admin, tipo("Cambiado", "99.99"));

		assertThat(r.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<String>read(r.getContentAsString(), "$.nombre")).isEqualTo("Cambiado");
		assertThat(enviar(HttpMethod.PUT, RUTA + "/999999", admin, tipo("Nada", "1")).getStatus()).isEqualTo(404);
	}

	@Test
	void editarNoPermiteUsarElNombreDeOtroTipo() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long id = crear(admin, "Otro tipo", "10");

		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + id, admin, tipo("pago de igss", "10")).getStatus()).isEqualTo(409);
	}

	@Test
	void eliminarMarcaElRegistroPeroNoLoBorraYLiberaElNombre() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long id = crear(admin, "Temporal", "10");

		assertThat(enviar(HttpMethod.DELETE, RUTA + "/" + id, admin, null).getStatus()).isEqualTo(204);

		assertThat(enviar(HttpMethod.GET, RUTA + "/" + id, admin, null).getStatus()).isEqualTo(404);
		List<String> nombres = JsonPath.read(enviar(HttpMethod.GET, RUTA, admin, null).getContentAsString(),
				"$.content[*].nombre");
		assertThat(nombres).doesNotContain("Temporal");
		Integer filasMarcadas = jdbc.queryForObject(
				"SELECT count(*) FROM tipo_tramite WHERE id = ? AND eliminado_en IS NOT NULL", Integer.class, id);
		assertThat(filasMarcadas).isEqualTo(1);
		assertThat(enviar(HttpMethod.DELETE, RUTA + "/" + id, admin, null).getStatus()).isEqualTo(404);
		assertThat(enviar(HttpMethod.POST, RUTA, admin, tipo("Temporal", "10")).getStatus()).isEqualTo(201);
	}

	@Test
	void unTipoInactivoSoloLoVeQuienPuedeEditar() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		String ciudadano = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crear(admin, "En pausa", "10");
		enviar(HttpMethod.PUT, RUTA + "/" + id, admin,
				"{\"nombre\":\"En pausa\",\"monto\":10,\"activo\":false}");

		assertThat(enviar(HttpMethod.GET, RUTA + "/" + id, ciudadano, null).getStatus()).isEqualTo(404);
		List<String> visiblesCiudadano = JsonPath.read(
				enviar(HttpMethod.GET, RUTA + "?activo=false", ciudadano, null).getContentAsString(), "$.content[*].nombre");
		assertThat(visiblesCiudadano).doesNotContain("En pausa");
		assertThat(enviar(HttpMethod.GET, RUTA + "/" + id, admin, null).getStatus()).isEqualTo(200);
		List<String> inactivosAdmin = JsonPath.read(
				enviar(HttpMethod.GET, RUTA + "?activo=false", admin, null).getContentAsString(), "$.content[*].nombre");
		assertThat(inactivosAdmin).contains("En pausa");
	}

	@Test
	void elListadoSePaginaYSeOrdena() throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		crear(admin, "Zeta", "1");
		crear(admin, "Alfa", "2");
		crear(admin, "Beta", "3");

		MockHttpServletResponse r = enviar(HttpMethod.GET, RUTA + "?size=2&page=0&sort=nombre,asc", admin, null);

		assertThat(r.getStatus()).isEqualTo(200);
		String cuerpo = r.getContentAsString();
		List<String> primera = JsonPath.read(cuerpo, "$.content[*].nombre");
		assertThat(primera).hasSize(2).isSorted();
		assertThat(JsonPath.<Integer>read(cuerpo, "$.page.size")).isEqualTo(2);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.page.totalElements")).isGreaterThanOrEqualTo(5);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.page.totalPages")).isGreaterThanOrEqualTo(3);
	}

	@Test
	void ordenarPorUnCampoNoPermitidoEs400() throws Exception {
		String ciudadano = tokenCon("ana@demo.com", "CIUDADANO");

		assertThat(enviar(HttpMethod.GET, RUTA + "?sort=hashContrasena,asc", ciudadano, null).getStatus()).isEqualTo(400);
	}

	@Test
	void elTamanoDePaginaTieneUnMaximoDe100() throws Exception {
		String ciudadano = tokenCon("ana@demo.com", "CIUDADANO");

		String cuerpo = enviar(HttpMethod.GET, RUTA + "?size=1000", ciudadano, null).getContentAsString();

		assertThat(JsonPath.<Integer>read(cuerpo, "$.page.size")).isLessThanOrEqualTo(100);
	}

}
