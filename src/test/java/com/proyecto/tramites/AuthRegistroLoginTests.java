package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.proyecto.tramites.auth.service.TokenService;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Tarjeta 8: registro publico de ciudadanos y login que devuelve los dos tokens. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class AuthRegistroLoginTests {

	private static final String CLAVE = "clave-segura-123";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder codificador;

	private MvcResult registrar(String nombre, String correo, String clave) throws Exception {
		String cuerpo = "{\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}";
		return mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andReturn();
	}

	private MvcResult login(String correo, String clave) throws Exception {
		String cuerpo = "{\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}";
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andReturn();
	}

	private static String contenido(MvcResult resultado) throws Exception {
		return resultado.getResponse().getContentAsString();
	}

	private static String cargaDelJwt(String jwt) {
		return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
	}

	@Test
	void elRegistroCreaUnCiudadanoYNoDevuelveLaClave() throws Exception {
		MvcResult resultado = registrar("Ana Lopez", "ana@demo.com", CLAVE);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
		String cuerpo = contenido(resultado);
		assertThat(JsonPath.<List<String>>read(cuerpo, "$.roles")).containsExactly("CIUDADANO");
		assertThat(cuerpo).doesNotContain(CLAVE).doesNotContain("hash").doesNotContain("$2");
	}

	@Test
	void laClaveSeGuardaConBCryptYNoEnTextoPlano() throws Exception {
		registrar("Ana Lopez", "ana@demo.com", CLAVE);

		Usuario ana = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull("ana@demo.com").orElseThrow();
		assertThat(ana.getHashContrasena()).isNotEqualTo(CLAVE).startsWith("$2");
		assertThat(codificador.matches(CLAVE, ana.getHashContrasena())).isTrue();
	}

	@Test
	void unCorreoRepetidoSinImportarMayusculasEs409() throws Exception {
		registrar("Ana Lopez", "ana@demo.com", CLAVE);

		assertThat(registrar("Otra Ana", "ANA@Demo.com", CLAVE).getResponse().getStatus()).isEqualTo(409);
	}

	@Test
	void unaClaveCortaEs400() throws Exception {
		assertThat(registrar("Ana Lopez", "ana@demo.com", "corta").getResponse().getStatus()).isEqualTo(400);
	}

	@Test
	void unaClaveRechazadaNoApareceEnLosLogs(CapturedOutput salida) throws Exception {
		// 7 caracteres: la validacion la rechaza (minimo 8) y Spring suele imprimir el valor rechazado
		registrar("Ana Lopez", "ana@demo.com", "secr-37");
		registrar("Ana Lopez", "ana@demo.com", CLAVE);
		login("ana@demo.com", "clave-mala-para-el-log");

		assertThat(salida.getAll()).doesNotContain("secr-37").doesNotContain("clave-mala-para-el-log")
				.doesNotContain(CLAVE);
	}

	@Test
	void unCorreoInvalidoEs400() throws Exception {
		assertThat(registrar("Ana Lopez", "esto-no-es-un-correo", CLAVE).getResponse().getStatus()).isEqualTo(400);
	}

	@Test
	void elLoginDevuelveLosDosTokens() throws Exception {
		registrar("Ana Lopez", "ana@demo.com", CLAVE);

		MvcResult resultado = login("ana@demo.com", CLAVE);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
		String cuerpo = contenido(resultado);
		String acceso = JsonPath.read(cuerpo, "$.accessToken");
		String renovacion = JsonPath.read(cuerpo, "$.refreshToken");
		assertThat(acceso.split("\\.")).hasSize(3);
		assertThat(renovacion).isNotBlank().isNotEqualTo(acceso);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.expiraEnSegundos")).isEqualTo(900);
	}

	@Test
	void elTokenDeAccesoLlevaIdRolesEmisionVencimientoYJtiPeroNoPermisos() throws Exception {
		registrar("Ana Lopez", "ana@demo.com", CLAVE);
		Usuario ana = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull("ana@demo.com").orElseThrow();

		String acceso = JsonPath.read(contenido(login("ana@demo.com", CLAVE)), "$.accessToken");
		String carga = cargaDelJwt(acceso);

		assertThat(JsonPath.<String>read(carga, "$.sub")).isEqualTo(String.valueOf(ana.getId()));
		assertThat(JsonPath.<List<String>>read(carga, "$.roles")).containsExactly("CIUDADANO");
		assertThat(JsonPath.<String>read(carga, "$.jti")).isNotBlank();
		long emision = JsonPath.<Number>read(carga, "$.iat").longValue();
		long vencimiento = JsonPath.<Number>read(carga, "$.exp").longValue();
		assertThat(vencimiento - emision).isEqualTo(15 * 60);
		assertThat(Instant.ofEpochSecond(emision)).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(30, ChronoUnit.SECONDS));
		assertThat(carga).doesNotContain("tramite:").doesNotContain("permisos").doesNotContain("authorities");
	}

	@Test
	void elTokenDeRenovacionSeGuardaSoloComoHash() throws Exception {
		registrar("Ana Lopez", "ana@demo.com", CLAVE);

		String renovacion = JsonPath.read(contenido(login("ana@demo.com", CLAVE)), "$.refreshToken");

		List<String> guardados = jdbc.queryForList("SELECT hash FROM token_renovacion", String.class);
		assertThat(guardados).containsExactly(TokenService.hash(renovacion));
		assertThat(guardados.get(0)).hasSize(64).isNotEqualTo(renovacion);
	}

	@Test
	void cadaLoginEmiteTokensDistintos() throws Exception {
		registrar("Ana Lopez", "ana@demo.com", CLAVE);

		String primero = JsonPath.read(contenido(login("ana@demo.com", CLAVE)), "$.refreshToken");
		String segundo = JsonPath.read(contenido(login("ana@demo.com", CLAVE)), "$.refreshToken");

		assertThat(primero).isNotEqualTo(segundo);
	}

	@Test
	void correoInexistenteYClaveIncorrectaResponden401ConElMismoMensaje() throws Exception {
		registrar("Ana Lopez", "ana@demo.com", CLAVE);

		MvcResult claveMala = login("ana@demo.com", "otra-clave-123");
		MvcResult sinCorreo = login("nadie@demo.com", CLAVE);

		assertThat(claveMala.getResponse().getStatus()).isEqualTo(401);
		assertThat(sinCorreo.getResponse().getStatus()).isEqualTo(401);
		assertThat(mensajeDe(claveMala)).isEqualTo(mensajeDe(sinCorreo));
		assertThat(contenido(claveMala)).doesNotContain("ana@demo.com");
	}

	@Test
	void unaCuentaInactivaNoEntra() throws Exception {
		registrar("Ana Lopez", "ana@demo.com", CLAVE);
		Usuario ana = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull("ana@demo.com").orElseThrow();
		ana.setActivo(false);
		usuarios.saveAndFlush(ana);

		assertThat(login("ana@demo.com", CLAVE).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void unaCuentaBloqueadaNoEntra() throws Exception {
		registrar("Ana Lopez", "ana@demo.com", CLAVE);
		Usuario ana = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull("ana@demo.com").orElseThrow();
		ana.setBloqueadoHasta(Instant.now().plus(15, ChronoUnit.MINUTES));
		usuarios.saveAndFlush(ana);

		assertThat(login("ana@demo.com", CLAVE).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void unUsuarioEliminadoNoEntra() throws Exception {
		registrar("Ana Lopez", "ana@demo.com", CLAVE);
		Usuario ana = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull("ana@demo.com").orElseThrow();
		ana.eliminarLogicamente();
		usuarios.saveAndFlush(ana);

		assertThat(login("ana@demo.com", CLAVE).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void sinCuerpoValidoElLoginEs400() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unaRutaProtegidaSinTokenNoDejaPasar() throws Exception {
		MvcResult resultado = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/tramites")).andReturn();

		assertThat(resultado.getResponse().getStatus()).isIn(401, 403);
	}

	/** Lo que el cliente ve como motivo del error, sin datos que cambian entre llamadas. */
	private static String mensajeDe(MvcResult resultado) throws Exception {
		String cuerpo = contenido(resultado);
		if (cuerpo.isBlank()) {
			return String.valueOf(resultado.getResponse().getErrorMessage());
		}
		Object detalle = JsonPath.parse(cuerpo).read("$.detail", Object.class);
		return String.valueOf(detalle);
	}

}
