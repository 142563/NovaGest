package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.jayway.jsonpath.JsonPath;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.RolRepository;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarjeta 22: el tipo de cambio se consulta al Banguat, se guarda y la segunda vez sale de la base.
 * El "Banguat" es un servidor local que responde con los XML reales de docs/banguat/muestras/: nunca se llama
 * al servicio en vivo. Muestras: dolar 01-03/10/2026 (incluye el sabado 03); euro 01, 02 y 05/10/2026 (sin
 * el fin de semana).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TipoCambioConBanguatTests {

	private static final String CLAVE = "clave-segura-123";
	private static final Path MUESTRAS = Path.of("docs", "banguat", "muestras");
	private static final AtomicInteger LLAMADAS = new AtomicInteger();
	private static HttpServer banguat;

	@DynamicPropertySource
	static void banguatLocal(DynamicPropertyRegistry propiedades) throws IOException {
		banguat = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		banguat.createContext("/variables/ws/TipoCambio.asmx", TipoCambioConBanguatTests::responder);
		banguat.start();
		String url = "http://" + banguat.getAddress().getHostString() + ":" + banguat.getAddress().getPort()
				+ "/variables/ws/TipoCambio.asmx";
		propiedades.add("app.banguat.url", () -> url);
	}

	@AfterAll
	static void apagar() {
		if (banguat != null) {
			banguat.stop(0);
		}
	}

	private static void responder(HttpExchange intercambio) throws IOException {
		LLAMADAS.incrementAndGet();
		String accion = String.valueOf(intercambio.getRequestHeaders().getFirst("SOAPAction"));
		intercambio.getRequestBody().readAllBytes();
		String archivo = accion.contains("TipoCambioRangoMoneda") ? "TipoCambioRangoMoneda.xml"
				: accion.contains("TipoCambioRango") ? "TipoCambioRango.xml" : "TipoCambioDia.xml";
		byte[] cuerpo = Files.readAllBytes(MUESTRAS.resolve(archivo));
		intercambio.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
		intercambio.sendResponseHeaders(200, cuerpo.length);
		try (OutputStream salida = intercambio.getResponseBody()) {
			salida.write(cuerpo);
		}
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private RolRepository roles;

	@Autowired
	private JdbcTemplate jdbc;

	private MockHttpServletResponse enviar(HttpMethod metodo, String ruta, String token, String cuerpo) throws Exception {
		var peticion = request(metodo, ruta).header("Authorization", "Bearer " + token);
		if (cuerpo != null) {
			peticion = peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		}
		return mvc.perform(peticion).andReturn().getResponse();
	}

	private String tokenDeCiudadano() throws Exception {
		String correo = "ana@demo.com";
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(
				"{\"nombre\":\"Ana Lopez\",\"correo\":\"" + correo + "\",\"clave\":\"" + CLAVE + "\"}"));
		Usuario usuario = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull(correo).orElseThrow();
		usuario.getRoles().clear();
		usuario.getRoles().add(roles.findByNombre("CIUDADANO").orElseThrow());
		usuarios.saveAndFlush(usuario);
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"clave\":\"" + CLAVE + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(cuerpo, "$.accessToken");
	}

	private int guardados(int moneda) {
		return jdbc.queryForObject("SELECT count(*) FROM tipo_cambio WHERE moneda = ?", Integer.class, moneda);
	}

	@Test
	void consultaAlBanguatGuardaLoQueLlegaYLaSegundaVezUsaLaBase() throws Exception {
		String ana = tokenDeCiudadano();
		int antes = LLAMADAS.get();

		String primera = enviar(HttpMethod.GET, "/api/divisas/tipo-cambio?moneda=EUR&fecha=2026-10-05", ana, null)
				.getContentAsString(StandardCharsets.UTF_8);

		assertThat(JsonPath.<String>read(primera, "$.fecha")).isEqualTo("2026-10-05");
		assertThat(JsonPath.<Number>read(primera, "$.valor").doubleValue()).isEqualTo(1.1189);
		assertThat(JsonPath.<Boolean>read(primera, "$.estimado")).isFalse();
		assertThat(guardados(24)).isEqualTo(3);
		assertThat(LLAMADAS.get() - antes).isEqualTo(1);

		String segunda = enviar(HttpMethod.GET, "/api/divisas/tipo-cambio?moneda=EUR&fecha=2026-10-05", ana, null)
				.getContentAsString(StandardCharsets.UTF_8);

		assertThat(JsonPath.<Number>read(segunda, "$.valor").doubleValue()).isEqualTo(1.1189);
		assertThat(LLAMADAS.get() - antes).isEqualTo(1);
	}

	@Test
	void unDomingoUsaElUltimoDiaConCotizacionSinMarcarloEstimado() throws Exception {
		String ana = tokenDeCiudadano();

		String cuerpo = enviar(HttpMethod.GET, "/api/divisas/tipo-cambio?moneda=EUR&fecha=2026-10-04", ana, null)
				.getContentAsString(StandardCharsets.UTF_8);

		assertThat(JsonPath.<String>read(cuerpo, "$.fecha")).isEqualTo("2026-10-02");
		assertThat(JsonPath.<Number>read(cuerpo, "$.valor").doubleValue()).isEqualTo(1.1275);
		assertThat(JsonPath.<Boolean>read(cuerpo, "$.estimado")).isFalse();
	}

	@Test
	void laProyeccionConsultaElDolarYElEuroAlBanguat() throws Exception {
		String ana = tokenDeCiudadano();
		assertThat(enviar(HttpMethod.POST, "/api/ingresos", ana, "{\"fecha\":\"2026-09-30\",\"montoGtq\":3000}")
				.getStatus()).isEqualTo(201);

		String cuerpo = enviar(HttpMethod.GET, "/api/ingresos/proyeccion?moneda=EUR&fecha=2026-10-02", ana, null)
				.getContentAsString(StandardCharsets.UTF_8);

		assertThat(JsonPath.<Number>read(cuerpo, "$.quetzalesPorDolar").doubleValue()).isEqualTo(7.64136);
		assertThat(JsonPath.<Number>read(cuerpo, "$.valorMoneda").doubleValue()).isEqualTo(1.1275);
		assertThat(JsonPath.<Number>read(cuerpo, "$.totalEnMoneda").doubleValue()).isEqualTo(348.20);
		assertThat(JsonPath.<Boolean>read(cuerpo, "$.estimado")).isFalse();
		assertThat(guardados(2)).isEqualTo(3);
		assertThat(guardados(24)).isEqualTo(3);
	}

}
