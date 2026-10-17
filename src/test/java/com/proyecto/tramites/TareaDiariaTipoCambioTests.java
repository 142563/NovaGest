package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;

import com.proyecto.tramites.divisas.dto.Moneda;
import com.proyecto.tramites.divisas.dto.ResultadoActualizacion;
import com.proyecto.tramites.divisas.service.ActualizacionTipoCambioService;
import com.proyecto.tramites.divisas.service.TareaDiariaTipoCambio;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarjeta 21: la actualizacion diaria guarda el tipo de cambio de todas las monedas, sigue aunque falle
 * alguna y deja el resultado en la bitacora. El "Banguat" es un servidor local con los XML reales de
 * docs/banguat/muestras/ (para otras monedas se reutiliza la muestra del euro cambiando el codigo).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TareaDiariaTipoCambioTests {

	private static final Path MUESTRAS = Path.of("docs", "banguat", "muestras");
	private static final Pattern MONEDA = Pattern.compile("<moneda>(\\d+)</moneda>");
	private static final AtomicInteger LLAMADAS = new AtomicInteger();
	private static final AtomicBoolean CAIDO = new AtomicBoolean();
	private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
	private static HttpServer banguat;

	@DynamicPropertySource
	static void banguatLocal(DynamicPropertyRegistry propiedades) throws IOException {
		banguat = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		banguat.createContext("/variables/ws/TipoCambio.asmx", TareaDiariaTipoCambioTests::responder);
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
		String peticion = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		byte[] cuerpo;
		int estado = 200;
		if (CAIDO.get()) {
			estado = 503;
			cuerpo = "Servicio no disponible".getBytes(StandardCharsets.UTF_8);
		}
		else if (accion.contains("TipoCambioRangoMoneda")) {
			Matcher m = MONEDA.matcher(peticion);
			String codigo = m.find() ? m.group(1) : "24";
			String muestra = Files.readString(MUESTRAS.resolve("TipoCambioRangoMoneda.xml"), StandardCharsets.UTF_8);
			cuerpo = muestra.replace("<moneda>24</moneda>", "<moneda>" + codigo + "</moneda>")
					.getBytes(StandardCharsets.UTF_8);
		}
		else {
			cuerpo = Files.readAllBytes(MUESTRAS.resolve("TipoCambioRango.xml"));
		}
		intercambio.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
		intercambio.sendResponseHeaders(estado, cuerpo.length);
		try (OutputStream salida = intercambio.getResponseBody()) {
			salida.write(cuerpo);
		}
	}

	@Autowired
	private ActualizacionTipoCambioService actualizacion;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ApplicationContext contexto;

	@AfterEach
	void levantarBanguat() {
		CAIDO.set(false);
	}

	private int guardadosDe(Moneda moneda) {
		return jdbc.queryForObject("SELECT count(*) FROM tipo_cambio WHERE moneda = ?", Integer.class,
				moneda.codigoBanguat());
	}

	private String ultimaBitacora() {
		return jdbc.queryForObject("SELECT despues::text FROM bitacora WHERE accion = 'ACTUALIZAR_TIPO_CAMBIO' "
				+ "AND actor_id IS NULL ORDER BY id DESC LIMIT 1", String.class);
	}

	@Test
	void guardaElTipoDeCambioDeTodasLasMonedas() {
		ResultadoActualizacion resultado = actualizacion.actualizar(LUNES);

		assertThat(resultado.completa()).isTrue();
		assertThat(resultado.actualizadas()).containsExactlyInAnyOrderElementsOf(
				Arrays.stream(Moneda.values()).map(Enum::name).toList());
		for (Moneda moneda : Moneda.values()) {
			assertThat(guardadosDe(moneda)).as("filas de " + moneda).isGreaterThan(0);
		}
	}

	@Test
	void dejaElResultadoEnLaBitacora() {
		actualizacion.actualizar(LUNES);

		assertThat(ultimaBitacora()).contains("2026-10-05").contains("actualizadas").contains("EUR").contains("USD");
	}

	/**
	 * Las monedas que ya tienen el valor del dia guardado no se vuelven a consultar. El dolar si, porque la
	 * muestra grabada del dolar llega solo hasta el sabado 03/10 y no trae el valor del lunes.
	 */
	@Test
	void unaSegundaVezElMismoDiaSoloConsultaLoQueFalta() {
		ResultadoActualizacion primera = actualizacion.actualizar(LUNES);
		int despuesDeLaPrimera = LLAMADAS.get();

		ResultadoActualizacion segunda = actualizacion.actualizar(LUNES);

		assertThat(primera.completa()).isTrue();
		assertThat(segunda.completa()).isTrue();
		assertThat(LLAMADAS.get() - despuesDeLaPrimera).isEqualTo(1);
	}

	@Test
	void siElBanguatCaeSigueConLasDemasYReportaEstimadasYSinDatos() {
		jdbc.update("INSERT INTO tipo_cambio (fecha, moneda, valor, fuente) VALUES (?, 2, ?, 'BANGUAT')",
				Date.valueOf("2026-10-02"), new BigDecimal("7.64136"));
		jdbc.update("INSERT INTO tipo_cambio (fecha, moneda, valor, fuente) VALUES (?, 24, ?, 'BANGUAT')",
				Date.valueOf("2026-10-02"), new BigDecimal("1.1275"));
		CAIDO.set(true);

		ResultadoActualizacion resultado = actualizacion.actualizar(LUNES);

		assertThat(resultado.completa()).isFalse();
		assertThat(resultado.actualizadas()).isEmpty();
		assertThat(resultado.estimadas()).containsExactlyInAnyOrder("USD", "EUR");
		assertThat(resultado.sinDatos()).hasSize(Moneda.values().length - 2).contains("JPY", "GBP");
		assertThat(ultimaBitacora()).contains("sinDatos").contains("JPY");
	}

	@Test
	void enPruebasLaTareaProgramadaNoSeRegistra() {
		assertThat(contexto.getBeanNamesForType(TareaDiariaTipoCambio.class)).isEmpty();
	}

	@Test
	void laTareaCorreDeLunesAViernesEnHoraDeGuatemala() throws Exception {
		Scheduled programacion = TareaDiariaTipoCambio.class.getMethod("ejecutar").getAnnotation(Scheduled.class);

		assertThat(programacion.cron()).contains("MON-FRI");
		assertThat(programacion.zone()).isEqualTo("America/Guatemala");
	}

}
