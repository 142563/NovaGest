package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.extension.ExtendWith;

/** Tarjeta 10: renovacion de tokens de un solo uso, cierre de sesion y bloqueo por intentos fallidos. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class SesionesTests {

	private static final String CLAVE = "clave-segura-123";
	private static final String CORREO = "ana@demo.com";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager em;

	private MockHttpServletResponse enviar(HttpMethod metodo, String ruta, String token, String cuerpo) throws Exception {
		var peticion = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(metodo, ruta);
		if (cuerpo != null) {
			peticion = peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		}
		if (token != null) {
			peticion = peticion.header("Authorization", "Bearer " + token);
		}
		return mvc.perform(peticion).andReturn().getResponse();
	}

	private void registrar(String correo) throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(
				"{\"nombre\":\"Persona Prueba\",\"correo\":\"" + correo + "\",\"clave\":\"" + CLAVE + "\"}"));
	}

	private MockHttpServletResponse login(String correo, String clave) throws Exception {
		return enviar(HttpMethod.POST, "/api/auth/login", null,
				"{\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}");
	}

	private MockHttpServletResponse refrescar(String refresco) throws Exception {
		return enviar(HttpMethod.POST, "/api/auth/refresh", null, "{\"refreshToken\":\"" + refresco + "\"}");
	}

	private static String campo(MockHttpServletResponse respuesta, String ruta) throws Exception {
		return JsonPath.read(respuesta.getContentAsString(), ruta);
	}

	/** Registra al usuario y devuelve la respuesta de un login correcto (accessToken y refreshToken). */
	private MockHttpServletResponse sesion(String correo) throws Exception {
		registrar(correo);
		MockHttpServletResponse r = login(correo, CLAVE);
		assertThat(r.getStatus()).isEqualTo(200);
		return r;
	}

	private int fallos(String correo) {
		return jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE correo = ?", Integer.class, correo);
	}

	private Instant bloqueadoHasta(String correo) {
		java.sql.Timestamp t = jdbc.queryForObject("SELECT bloqueado_hasta FROM usuario WHERE correo = ?",
				java.sql.Timestamp.class, correo);
		return t == null ? null : t.toInstant();
	}

	private void fallar(String correo, int veces) throws Exception {
		for (int i = 0; i < veces; i++) {
			assertThat(login(correo, "clave-equivocada-" + i).getStatus()).isEqualTo(401);
		}
	}

	private int cuantasAcciones(String accion) {
		return jdbc.queryForObject("SELECT count(*) FROM bitacora WHERE accion = ?", Integer.class, accion);
	}

	// ---------- Renovacion de tokens ----------

	@Test
	void renovarEntregaUnParNuevoYElTokenViejoQuedaUsado() throws Exception {
		MockHttpServletResponse inicial = sesion(CORREO);
		String viejo = campo(inicial, "$.refreshToken");

		MockHttpServletResponse r = refrescar(viejo);

		assertThat(r.getStatus()).isEqualTo(200);
		String nuevoAcceso = campo(r, "$.accessToken");
		String nuevoRefresco = campo(r, "$.refreshToken");
		assertThat(nuevoRefresco).isNotEqualTo(viejo);
		assertThat(nuevoAcceso).isNotEqualTo(campo(inicial, "$.accessToken"));
		assertThat(enviar(HttpMethod.GET, "/api/usuarios/yo", nuevoAcceso, null).getStatus()).isEqualTo(200);
		Integer usados = jdbc.queryForObject("SELECT count(*) FROM token_renovacion WHERE usado_en IS NOT NULL", Integer.class);
		Integer total = jdbc.queryForObject("SELECT count(*) FROM token_renovacion", Integer.class);
		assertThat(usados).isEqualTo(1);
		assertThat(total).isEqualTo(2);
	}

	@Test
	void unTokenDeRenovacionUsadoDosVecesSeRechaza() throws Exception {
		String refresco = campo(sesion(CORREO), "$.refreshToken");
		assertThat(refrescar(refresco).getStatus()).isEqualTo(200);

		assertThat(refrescar(refresco).getStatus()).isEqualTo(401);
	}

	@Test
	void presentarUnTokenYaUsadoRevocaTodosLosTokensDeEseUsuario() throws Exception {
		MockHttpServletResponse sesionA = sesion(CORREO);
		MockHttpServletResponse sesionB = login(CORREO, CLAVE);
		String refrescoA = campo(sesionA, "$.refreshToken");
		String refrescoB = campo(sesionB, "$.refreshToken");
		String refrescoA2 = campo(refrescar(refrescoA), "$.refreshToken");

		assertThat(refrescar(refrescoA).getStatus()).isEqualTo(401); // reuso: alguien lo copio

		assertThat(refrescar(refrescoB).getStatus()).isEqualTo(401); // la otra sesion tambien cae
		assertThat(refrescar(refrescoA2).getStatus()).isEqualTo(401); // y el token nuevo de la primera
		Integer sinRevocar = jdbc.queryForObject("SELECT count(*) FROM token_renovacion WHERE revocado_en IS NULL", Integer.class);
		assertThat(sinRevocar).isZero();
	}

	@Test
	void elReusoDejaConstanciaEnLaBitacoraSinDatosSensibles(CapturedOutput salida) throws Exception {
		String refresco = campo(sesion(CORREO), "$.refreshToken");
		refrescar(refresco);
		refrescar(refresco);

		assertThat(cuantasAcciones("REUSO_TOKEN_RENOVACION")).isEqualTo(1);
		String detalle = jdbc.queryForObject("SELECT despues::text FROM bitacora WHERE accion = 'REUSO_TOKEN_RENOVACION'",
				String.class);
		assertThat(detalle).doesNotContain(refresco);
		assertThat(salida.getAll()).doesNotContain(refresco);
	}

	@Test
	void elReusoNoAfectaALosTokensDeOtrosUsuarios() throws Exception {
		String deAna = campo(sesion(CORREO), "$.refreshToken");
		String deLuis = campo(sesion("luis@demo.com"), "$.refreshToken");
		refrescar(deAna);
		refrescar(deAna); // reuso de Ana

		assertThat(refrescar(deLuis).getStatus()).isEqualTo(200);
	}

	@Test
	void unTokenInventadoOVacioOMalformadoNoSirve() throws Exception {
		sesion(CORREO);

		MockHttpServletResponse inventado = refrescar("token-que-nunca-existio");
		assertThat(inventado.getStatus()).isEqualTo(401);
		assertThat(JsonPath.<String>read(inventado.getContentAsString(), "$.detail")).isEqualTo("Token de renovacion invalido");
		assertThat(enviar(HttpMethod.POST, "/api/auth/refresh", null, "{\"refreshToken\":\"\"}").getStatus()).isEqualTo(400);
		assertThat(enviar(HttpMethod.POST, "/api/auth/refresh", null, "{}").getStatus()).isEqualTo(400);
		assertThat(enviar(HttpMethod.POST, "/api/auth/refresh", null, "no es json").getStatus()).isEqualTo(400);
	}

	@Test
	void todosLosFallosDeRenovacionDicenLoMismo() throws Exception {
		String refresco = campo(sesion(CORREO), "$.refreshToken");
		refrescar(refresco);

		String usado = refrescar(refresco).getContentAsString();
		String inventado = refrescar("otro-token-inventado").getContentAsString();

		assertThat(JsonPath.<String>read(usado, "$.detail")).isEqualTo(JsonPath.<String>read(inventado, "$.detail"));
	}

	@Test
	void unTokenDeRenovacionVencidoNoSirve() throws Exception {
		String refresco = campo(sesion(CORREO), "$.refreshToken");
		em.flush();
		jdbc.update("UPDATE token_renovacion SET vence_en = now() - interval '1 minute'");
		em.clear();

		assertThat(refrescar(refresco).getStatus()).isEqualTo(401);
	}

	@Test
	void unTokenDeAccesoNoSirveComoTokenDeRenovacionNiAlReves() throws Exception {
		MockHttpServletResponse r = sesion(CORREO);

		assertThat(refrescar(campo(r, "$.accessToken")).getStatus()).isEqualTo(401);
		assertThat(enviar(HttpMethod.GET, "/api/usuarios/yo", campo(r, "$.refreshToken"), null).getStatus()).isEqualTo(401);
	}

	@ParameterizedTest
	@CsvSource({ "INACTIVO", "ELIMINADO", "BLOQUEADO" })
	void siLaCuentaYaNoEstaHabilitadaLaRenovacionFallaEnElActo(String situacion) throws Exception {
		String refresco = campo(sesion(CORREO), "$.refreshToken");
		em.flush();
		switch (situacion) {
			case "INACTIVO" -> jdbc.update("UPDATE usuario SET activo = false WHERE correo = ?", CORREO);
			case "ELIMINADO" -> jdbc.update("UPDATE usuario SET eliminado_en = now(), activo = false WHERE correo = ?", CORREO);
			default -> jdbc.update("UPDATE usuario SET bloqueado_hasta = now() + interval '10 minutes' WHERE correo = ?", CORREO);
		}
		em.clear();

		assertThat(refrescar(refresco).getStatus()).isEqualTo(401);
	}

	// ---------- Cierre de sesion ----------

	@Test
	void cerrarUnaSesionRevocaSoloEseTokenDeRenovacion() throws Exception {
		MockHttpServletResponse sesionA = sesion(CORREO);
		MockHttpServletResponse sesionB = login(CORREO, CLAVE);
		String accesoA = campo(sesionA, "$.accessToken");

		MockHttpServletResponse r = enviar(HttpMethod.POST, "/api/auth/logout", accesoA,
				"{\"refreshToken\":\"" + campo(sesionA, "$.refreshToken") + "\"}");

		assertThat(r.getStatus()).isEqualTo(204);
		assertThat(refrescar(campo(sesionA, "$.refreshToken")).getStatus()).isEqualTo(401);
		assertThat(refrescar(campo(sesionB, "$.refreshToken")).getStatus()).isEqualTo(200);
	}

	@Test
	void cerrarSesionSinIndicarTokenRevocaTodosLosDelUsuario() throws Exception {
		MockHttpServletResponse sesionA = sesion(CORREO);
		MockHttpServletResponse sesionB = login(CORREO, CLAVE);
		String otro = campo(sesion("luis@demo.com"), "$.refreshToken");

		assertThat(enviar(HttpMethod.POST, "/api/auth/logout", campo(sesionA, "$.accessToken"), null).getStatus())
				.isEqualTo(204);

		assertThat(refrescar(campo(sesionA, "$.refreshToken")).getStatus()).isEqualTo(401);
		assertThat(refrescar(campo(sesionB, "$.refreshToken")).getStatus()).isEqualTo(401);
		assertThat(refrescar(otro).getStatus()).isEqualTo(200);
		assertThat(cuantasAcciones("CIERRE_SESION")).isEqualTo(1);
	}

	@Test
	void cerrarSesionExigeEstarAutenticado() throws Exception {
		MockHttpServletResponse r = sesion(CORREO);

		assertThat(enviar(HttpMethod.POST, "/api/auth/logout", null,
				"{\"refreshToken\":\"" + campo(r, "$.refreshToken") + "\"}").getStatus()).isEqualTo(401);
		assertThat(refrescar(campo(r, "$.refreshToken")).getStatus()).isEqualTo(200); // no se toco
	}

	@Test
	void nadiePuedeRevocarElTokenDeOtroUsuarioNiSabeSiExiste() throws Exception {
		MockHttpServletResponse ana = sesion(CORREO);
		MockHttpServletResponse luis = sesion("luis@demo.com");

		MockHttpServletResponse r = enviar(HttpMethod.POST, "/api/auth/logout", campo(luis, "$.accessToken"),
				"{\"refreshToken\":\"" + campo(ana, "$.refreshToken") + "\"}");
		MockHttpServletResponse inventado = enviar(HttpMethod.POST, "/api/auth/logout", campo(luis, "$.accessToken"),
				"{\"refreshToken\":\"no-existe\"}");

		assertThat(r.getStatus()).isEqualTo(204);
		assertThat(inventado.getStatus()).isEqualTo(204);
		assertThat(refrescar(campo(ana, "$.refreshToken")).getStatus()).isEqualTo(200); // el de Ana sigue vivo
	}

	@Test
	void elTokenDeAccesoYaEmitidoSigueValidoHastaVencerPorqueLaApiNoGuardaSesiones() throws Exception {
		MockHttpServletResponse r = sesion(CORREO);
		String acceso = campo(r, "$.accessToken");
		enviar(HttpMethod.POST, "/api/auth/logout", acceso, null);

		assertThat(enviar(HttpMethod.GET, "/api/usuarios/yo", acceso, null).getStatus()).isEqualTo(200);
		assertThat(refrescar(campo(r, "$.refreshToken")).getStatus()).isEqualTo(401);
	}

	// ---------- Bloqueo por intentos ----------

	@Test
	void cincoFallosSeguidosBloqueanLaCuentaYLaClaveCorrectaYaNoEntra() throws Exception {
		registrar(CORREO);

		fallar(CORREO, 5);

		assertThat(login(CORREO, CLAVE).getStatus()).isEqualTo(401);
		Instant hasta = bloqueadoHasta(CORREO);
		assertThat(hasta).isNotNull();
		assertThat(hasta).isBetween(Instant.now().plusSeconds(14 * 60), Instant.now().plusSeconds(15 * 60 + 30));
		assertThat(cuantasAcciones("BLOQUEO_CUENTA")).isEqualTo(1);
	}

	@Test
	void cuatroFallosNoBloqueanYUnInicioCorrectoReiniciaElContador() throws Exception {
		registrar(CORREO);
		fallar(CORREO, 4);
		assertThat(fallos(CORREO)).isEqualTo(4);
		assertThat(bloqueadoHasta(CORREO)).isNull();

		assertThat(login(CORREO, CLAVE).getStatus()).isEqualTo(200);

		assertThat(fallos(CORREO)).isZero();
		fallar(CORREO, 4); // vuelve a empezar desde cero: otros cuatro fallos no bloquean
		assertThat(bloqueadoHasta(CORREO)).isNull();
		assertThat(login(CORREO, CLAVE).getStatus()).isEqualTo(200);
	}

	@Test
	void elBloqueoNoRevelaQueLaCuentaEstaBloqueada() throws Exception {
		registrar(CORREO);
		MockHttpServletResponse normal = login(CORREO, "clave-equivocada");
		fallar(CORREO, 4);

		MockHttpServletResponse bloqueada = login(CORREO, CLAVE);
		MockHttpServletResponse inexistente = login("nadie@demo.com", CLAVE);

		assertThat(JsonPath.<String>read(bloqueada.getContentAsString(), "$.detail"))
				.isEqualTo(JsonPath.<String>read(normal.getContentAsString(), "$.detail"))
				.isEqualTo(JsonPath.<String>read(inexistente.getContentAsString(), "$.detail"));
	}

	@Test
	void mientrasLaCuentaEstaBloqueadaLosNuevosFallosNoLaExtienden() throws Exception {
		registrar(CORREO);
		fallar(CORREO, 5);
		Instant primera = bloqueadoHasta(CORREO);

		fallar(CORREO, 3);

		assertThat(bloqueadoHasta(CORREO)).isEqualTo(primera);
		assertThat(fallos(CORREO)).isZero();
	}

	@Test
	void cuandoPasaElBloqueoLaClaveCorrectaVuelveAEntrar() throws Exception {
		registrar(CORREO);
		fallar(CORREO, 5);
		em.flush();
		jdbc.update("UPDATE usuario SET bloqueado_hasta = now() - interval '1 minute' WHERE correo = ?", CORREO);
		em.clear();

		assertThat(login(CORREO, CLAVE).getStatus()).isEqualTo(200);
		assertThat(bloqueadoHasta(CORREO)).isNull();
	}

	@Test
	void despuesDeUnBloqueoVencidoHacenFaltaCincoFallosMasParaBloquearDeNuevo() throws Exception {
		registrar(CORREO);
		fallar(CORREO, 5);
		em.flush();
		jdbc.update("UPDATE usuario SET bloqueado_hasta = now() - interval '1 minute' WHERE correo = ?", CORREO);
		em.clear();

		fallar(CORREO, 4);

		assertThat(bloqueadoHasta(CORREO)).isNotNull().isBefore(Instant.now()); // el bloqueo viejo no se renovo
		assertThat(fallos(CORREO)).isEqualTo(4);
		assertThat(login(CORREO, CLAVE).getStatus()).isEqualTo(200);
	}

	@Test
	void losFallosDeUnUsuarioNoAfectanAOtro() throws Exception {
		registrar(CORREO);
		registrar("luis@demo.com");

		fallar(CORREO, 5);

		assertThat(login("luis@demo.com", CLAVE).getStatus()).isEqualTo(200);
		assertThat(fallos("luis@demo.com")).isZero();
	}

	@Test
	void probarUnCorreoQueNoExisteNoCreaNiBloqueaNada() throws Exception {
		registrar(CORREO);

		for (int i = 0; i < 8; i++) {
			assertThat(login("nadie@demo.com", "clave-equivocada").getStatus()).isEqualTo(401);
		}

		assertThat(cuantasAcciones("BLOQUEO_CUENTA")).isZero();
		assertThat(login(CORREO, CLAVE).getStatus()).isEqualTo(200);
	}

	@Test
	void losFallosDeUnaCuentaInactivaNoSuman() throws Exception {
		registrar(CORREO);
		em.flush();
		jdbc.update("UPDATE usuario SET activo = false WHERE correo = ?", CORREO);
		em.clear();

		fallar(CORREO, 6);

		assertThat(fallos(CORREO)).isZero();
		assertThat(bloqueadoHasta(CORREO)).isNull();
	}

	@Test
	void unaClaveIncorrectaNuncaApareceEnLosLogsNiEnLaBitacora(CapturedOutput salida) throws Exception {
		registrar(CORREO);

		fallar(CORREO, 5);

		assertThat(salida.getAll()).doesNotContain("clave-equivocada-0").doesNotContain("clave-equivocada-4");
		List<String> detalles = jdbc.queryForList("SELECT coalesce(antes::text,'') || coalesce(despues::text,'') FROM bitacora",
				String.class);
		assertThat(String.join("", detalles)).doesNotContain("clave-equivocada");
	}

}
