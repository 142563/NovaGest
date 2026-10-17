package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.jayway.jsonpath.JsonPath;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.RolRepository;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
 * Tarjeta 22: proyeccion de ingresos y tipo de cambio. En este perfil el Banguat no responde (ver
 * application-test.properties), asi que los tipos de cambio se cargan en la base como si ya se hubieran
 * consultado. Con eso se prueban el calculo, el respaldo "estimado" y el 503 cuando no hay ningun dato.
 * Valores reales del 02/10/2026 (docs/investigacion-apis-publicas.md): dolar 7.64136, euro 1.1275, yen 157.52.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProyeccionDivisasTests {

	private static final String CLAVE = "clave-segura-123";
	private static final String DIA = "2026-10-02";

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

	private void ingreso(String token, String fecha, String monto) throws Exception {
		MockHttpServletResponse r = enviar(HttpMethod.POST, "/api/ingresos", token,
				"{\"fecha\":\"" + fecha + "\",\"montoGtq\":" + monto + "}");
		assertThat(r.getStatus()).isEqualTo(201);
	}

	private void guardarTipoDeCambio(String fecha, int moneda, String valor) {
		jdbc.update("INSERT INTO tipo_cambio (fecha, moneda, valor, fuente) VALUES (?, ?, ?, 'BANGUAT')",
				Date.valueOf(fecha), moneda, new BigDecimal(valor));
	}

	private void guardarValoresDel2DeOctubre() {
		guardarTipoDeCambio(DIA, 2, "7.64136");
		guardarTipoDeCambio(DIA, 24, "1.1275");
		guardarTipoDeCambio(DIA, 3, "157.52");
	}

	private String json(MockHttpServletResponse r) throws Exception {
		return r.getContentAsString(StandardCharsets.UTF_8);
	}

	private static double numero(String json, String ruta) {
		return JsonPath.<Number>read(json, ruta).doubleValue();
	}

	// ---------------------------------------------------------------- monedas y tipo de cambio

	@Test
	void listaLasMonedasConSuConvencion() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		String cuerpo = json(enviar(HttpMethod.GET, "/api/divisas/monedas", ana, null));

		assertThat(JsonPath.<List<String>>read(cuerpo, "$[*].codigo")).contains("USD", "EUR", "GBP", "JPY")
				.hasSize(11);
		assertThat(JsonPath.<List<String>>read(cuerpo, "$[?(@.codigo=='EUR')].convencion"))
				.containsExactly("DOLARES_POR_UNIDAD");
		assertThat(JsonPath.<List<String>>read(cuerpo, "$[?(@.codigo=='JPY')].convencion"))
				.containsExactly("UNIDADES_POR_DOLAR");
		assertThat(JsonPath.<List<String>>read(cuerpo, "$[?(@.codigo=='USD')].convencion"))
				.containsExactly("QUETZALES_POR_DOLAR");
	}

	@Test
	void devuelveElTipoDeCambioGuardadoDeEsaFecha() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		guardarValoresDel2DeOctubre();

		MockHttpServletResponse r = enviar(HttpMethod.GET, "/api/divisas/tipo-cambio?moneda=eur&fecha=" + DIA, ana,
				null);

		assertThat(r.getStatus()).isEqualTo(200);
		String cuerpo = json(r);
		assertThat(JsonPath.<String>read(cuerpo, "$.moneda")).isEqualTo("EUR");
		assertThat(numero(cuerpo, "$.valor")).isEqualTo(1.1275);
		assertThat(JsonPath.<String>read(cuerpo, "$.fecha")).isEqualTo(DIA);
		assertThat(JsonPath.<Boolean>read(cuerpo, "$.estimado")).isFalse();
		assertThat(JsonPath.<String>read(cuerpo, "$.fuente")).isEqualTo("BANGUAT");
	}

	@Test
	void sinBanguatUsaElUltimoValorGuardadoYLoMarcaComoEstimado() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		guardarValoresDel2DeOctubre();

		String cuerpo = json(enviar(HttpMethod.GET, "/api/divisas/tipo-cambio?moneda=EUR&fecha=2026-10-06", ana,
				null));

		assertThat(JsonPath.<Boolean>read(cuerpo, "$.estimado")).isTrue();
		assertThat(JsonPath.<String>read(cuerpo, "$.fecha")).isEqualTo(DIA);
		assertThat(JsonPath.<String>read(cuerpo, "$.fechaSolicitada")).isEqualTo("2026-10-06");
		assertThat(numero(cuerpo, "$.valor")).isEqualTo(1.1275);
	}

	@Test
	void sinBanguatYSinNadaGuardadoResponde503() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		MockHttpServletResponse r = enviar(HttpMethod.GET, "/api/divisas/tipo-cambio?moneda=CHF&fecha=" + DIA, ana,
				null);

		assertThat(r.getStatus()).isEqualTo(503);
		assertThat(r.getContentType()).startsWith("application/problem+json");
	}

	@Test
	void unaMonedaQueNoSeSoportaEs400SinRepetirElValor() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		MockHttpServletResponse r = enviar(HttpMethod.GET, "/api/divisas/tipo-cambio?moneda=XYZ-INVENTADA", ana,
				null);

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(JsonPath.<List<String>>read(json(r), "$.errores[*].campo")).contains("moneda");
		assertThat(json(r)).doesNotContain("XYZ-INVENTADA");
	}

	@Test
	void unaFechaFuturaEs400() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String manana = LocalDate.now(ZoneId.of("America/Guatemala")).plusDays(1).toString();

		assertThat(enviar(HttpMethod.GET, "/api/divisas/tipo-cambio?moneda=EUR&fecha=" + manana, ana, null)
				.getStatus()).isEqualTo(400);
	}

	// ---------------------------------------------------------------- proyeccion

	@Test
	void tresMilQuetzalesSon348EurosConElTipoDeCambioDel2DeOctubre() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		guardarValoresDel2DeOctubre();
		ingreso(ana, "2026-09-15", "1000");
		ingreso(ana, "2026-09-30", "2000");

		MockHttpServletResponse r = enviar(HttpMethod.GET, "/api/ingresos/proyeccion?moneda=EUR&fecha=" + DIA, ana,
				null);

		assertThat(r.getStatus()).isEqualTo(200);
		String cuerpo = json(r);
		assertThat(numero(cuerpo, "$.totalGtq")).isEqualTo(3000.00);
		assertThat(numero(cuerpo, "$.totalEnMoneda")).isEqualTo(348.20);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.cantidadIngresos")).isEqualTo(2);
		assertThat(numero(cuerpo, "$.quetzalesPorDolar")).isEqualTo(7.64136);
		assertThat(numero(cuerpo, "$.valorMoneda")).isEqualTo(1.1275);
		assertThat(JsonPath.<String>read(cuerpo, "$.convencion")).isEqualTo("DOLARES_POR_UNIDAD");
		assertThat(JsonPath.<Boolean>read(cuerpo, "$.estimado")).isFalse();
	}

	@Test
	void enYenesSeMultiplicaPorqueSeCotizaEnUnidadesPorDolar() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		guardarValoresDel2DeOctubre();
		ingreso(ana, "2026-09-30", "3000");

		String cuerpo = json(enviar(HttpMethod.GET, "/api/ingresos/proyeccion?moneda=JPY&fecha=" + DIA, ana, null));

		assertThat(numero(cuerpo, "$.totalEnMoneda")).isEqualTo(61842.39);
	}

	@Test
	void enDolaresSoloSeDividePorElTipoDeCambio() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		guardarValoresDel2DeOctubre();
		ingreso(ana, "2026-09-30", "3000");

		String cuerpo = json(enviar(HttpMethod.GET, "/api/ingresos/proyeccion?moneda=USD&fecha=" + DIA, ana, null));

		assertThat(numero(cuerpo, "$.totalEnMoneda")).isEqualTo(392.60);
		assertThat(JsonPath.<Object>read(cuerpo, "$.valorMoneda")).isNull();
	}

	@Test
	void soloSumaLosIngresosPropiosYRespetaElRangoDeFechas() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String luis = tokenCon("luis@demo.com", "CIUDADANO");
		guardarValoresDel2DeOctubre();
		ingreso(ana, "2026-08-15", "500");
		ingreso(ana, "2026-09-15", "3000");
		ingreso(luis, "2026-09-15", "99999");

		String todo = json(enviar(HttpMethod.GET, "/api/ingresos/proyeccion?moneda=EUR&fecha=" + DIA, ana, null));
		String septiembre = json(enviar(HttpMethod.GET,
				"/api/ingresos/proyeccion?moneda=EUR&fecha=" + DIA + "&desde=2026-09-01&hasta=2026-09-30", ana, null));

		assertThat(numero(todo, "$.totalGtq")).isEqualTo(3500.00);
		assertThat(numero(septiembre, "$.totalGtq")).isEqualTo(3000.00);
		assertThat(JsonPath.<String>read(septiembre, "$.desde")).isEqualTo("2026-09-01");
	}

	@Test
	void sinIngresosLaProyeccionEsCero() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		guardarValoresDel2DeOctubre();

		String cuerpo = json(enviar(HttpMethod.GET, "/api/ingresos/proyeccion?moneda=EUR&fecha=" + DIA, ana, null));

		assertThat(numero(cuerpo, "$.totalEnMoneda")).isZero();
		assertThat(JsonPath.<Integer>read(cuerpo, "$.cantidadIngresos")).isZero();
	}

	@Test
	void conValoresAnterioresLaProyeccionQuedaMarcadaComoEstimada() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		guardarValoresDel2DeOctubre();
		ingreso(ana, "2026-09-30", "3000");

		String cuerpo = json(enviar(HttpMethod.GET, "/api/ingresos/proyeccion?moneda=EUR&fecha=2026-10-07", ana,
				null));

		assertThat(JsonPath.<Boolean>read(cuerpo, "$.estimado")).isTrue();
		assertThat(numero(cuerpo, "$.totalEnMoneda")).isEqualTo(348.20);
		assertThat(JsonPath.<String>read(cuerpo, "$.fechaMoneda")).isEqualTo(DIA);
	}

	@Test
	void laProyeccionPideMoneda() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		MockHttpServletResponse r = enviar(HttpMethod.GET, "/api/ingresos/proyeccion", ana, null);

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(JsonPath.<List<String>>read(json(r), "$.errores[*].campo")).contains("moneda");
	}

	@Test
	void fechasInvalidasEnLaProyeccionSon400SinRepetirElValor() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");

		MockHttpServletResponse r = enviar(HttpMethod.GET,
				"/api/ingresos/proyeccion?moneda=EUR&desde=nunca-XYZ&fecha=2026-99-99", ana, null);

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(json(r)).doesNotContain("nunca-XYZ").doesNotContain("2026-99-99");
	}

	// ---------------------------------------------------------------- permisos

	@ParameterizedTest
	@ValueSource(strings = { "GESTOR", "AUDITOR", "ADMINISTRADOR" })
	void soloElCiudadanoPuedeProyectarYConsultarDivisas(String rol) throws Exception {
		String token = tokenCon("otro@demo.com", rol);

		assertThat(enviar(HttpMethod.GET, "/api/ingresos/proyeccion?moneda=EUR", token, null).getStatus())
				.isEqualTo(403);
		assertThat(enviar(HttpMethod.GET, "/api/divisas/monedas", token, null).getStatus()).isEqualTo(403);
		assertThat(enviar(HttpMethod.GET, "/api/divisas/tipo-cambio?moneda=EUR", token, null).getStatus())
				.isEqualTo(403);
	}

	@Test
	void sinTokenEs401() throws Exception {
		assertThat(enviar(HttpMethod.GET, "/api/ingresos/proyeccion?moneda=EUR", null, null).getStatus())
				.isEqualTo(401);
		assertThat(enviar(HttpMethod.GET, "/api/divisas/monedas", null, null).getStatus()).isEqualTo(401);
	}

}
