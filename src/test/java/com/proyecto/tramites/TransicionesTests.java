package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.jayway.jsonpath.JsonPath;
import com.proyecto.tramites.auditoria.service.BitacoraService;
import com.proyecto.tramites.tramites.entity.EstadoTramite;
import com.proyecto.tramites.tramites.entity.TransicionTramite;
import com.proyecto.tramites.tramites.service.ConsultaPagos;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.RolRepository;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Tarjeta 16: maquina de estados con solo siete transiciones validas, historial y bitacora. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransicionesTests {

	private static final String CLAVE = "clave-segura-123";
	private static final String RUTA = "/api/tramites";
	private static final String DATOS_COMPLETOS =
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

	@Autowired
	private BitacoraService bitacoraService;

	/** Reemplaza al modulo de pagos (tarjeta 18) para decidir si un tramite tiene pago validado. */
	@MockitoBean
	private ConsultaPagos pagos;

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

	private long crearTramite(String token, String datos) throws Exception {
		String tipos = enviar(HttpMethod.GET, "/api/tipos-tramite?size=100", token, null).getContentAsString();
		long ornato = JsonPath.<List<Number>>read(tipos, "$.content[?(@.nombre=='Boleto de ornato')].id").get(0).longValue();
		MockHttpServletResponse r = enviar(HttpMethod.POST, RUTA, token,
				"{\"tipoId\":" + ornato + ",\"datos\":" + datos + "}");
		assertThat(r.getStatus()).isEqualTo(201);
		return JsonPath.<Number>read(r.getContentAsString(), "$.id").longValue();
	}

	private void ponerEstado(long idTramite, String estado) {
		em.flush();
		jdbc.update("UPDATE tramite SET estado = ? WHERE id = ?", estado, idTramite);
		em.clear();
	}

	private MockHttpServletResponse transicionar(String token, long id, String destino, String comentario) throws Exception {
		String cuerpo = comentario == null
				? "{\"estadoDestino\":\"" + destino + "\"}"
				: "{\"estadoDestino\":\"" + destino + "\",\"comentario\":\"" + comentario + "\"}";
		return enviar(HttpMethod.POST, RUTA + "/" + id + "/transiciones", token, cuerpo);
	}

	private int filas(String tabla, long idTramite) {
		String columna = tabla.equals("bitacora") ? "recurso_id" : "tramite_id";
		String filtro = tabla.equals("bitacora") ? " AND recurso = 'tramite'" : "";
		return jdbc.queryForObject("SELECT count(*) FROM " + tabla + " WHERE " + columna + " = ?" + filtro,
				Integer.class, idTramite);
	}

	// ---------- La maquina: solo siete transiciones ----------

	@Test
	void existenExactamenteSieteTransicionesYSinRepetirse() {
		assertThat(TransicionTramite.values()).hasSize(7);
		long distintas = Stream.of(TransicionTramite.values()).map(t -> t.desde() + ">" + t.hasta()).distinct().count();
		assertThat(distintas).isEqualTo(7);
	}

	@Test
	void losEstadosFinalesNoTienenSalida() {
		assertThat(TransicionTramite.destinosDesde(EstadoTramite.FINALIZADO)).isEmpty();
		assertThat(TransicionTramite.destinosDesde(EstadoTramite.RECHAZADO)).isEmpty();
	}

	static Stream<Arguments> transicionesQueNoExisten() {
		return Stream.of(EstadoTramite.values()).flatMap(desde -> Stream.of(EstadoTramite.values())
				.filter(hasta -> TransicionTramite.buscar(desde, hasta).isEmpty())
				.map(hasta -> Arguments.of(desde.name(), hasta.name())));
	}

	@ParameterizedTest(name = "{0} a {1} responde 409")
	@MethodSource("transicionesQueNoExisten")
	void cualquierTransicionQueNoEsUnaDeLasSieteResponde409(String desde, String hasta) throws Exception {
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long id = crearTramite(admin, DATOS_COMPLETOS);
		ponerEstado(id, desde);

		assertThat(transicionar(admin, id, hasta, "x").getStatus()).isEqualTo(409);
		String estado = JsonPath.read(enviar(HttpMethod.GET, RUTA + "/" + id, admin, null).getContentAsString(), "$.estado");
		assertThat(estado).isEqualTo(desde);
	}

	// ---------- Quien puede hacer cada una ----------

	@ParameterizedTest(name = "{0} a {1} como {2} responde {3}")
	@CsvSource({
			"BORRADOR,ENVIADO,DUENA,200", "BORRADOR,ENVIADO,OTRO_CIUDADANO,404", "BORRADOR,ENVIADO,GESTOR,403",
			"BORRADOR,ENVIADO,ADMIN,403", "BORRADOR,ENVIADO,AUDITOR,403",
			"ENVIADO,EN_REVISION,GESTOR,200", "ENVIADO,EN_REVISION,ADMIN,200", "ENVIADO,EN_REVISION,DUENA,403",
			"ENVIADO,EN_REVISION,AUDITOR,403", "ENVIADO,EN_REVISION,OTRO_CIUDADANO,404",
			"EN_REVISION,OBSERVADO,GESTOR,200", "EN_REVISION,OBSERVADO,DUENA,403", "EN_REVISION,OBSERVADO,AUDITOR,403",
			"OBSERVADO,EN_REVISION,DUENA,200", "OBSERVADO,EN_REVISION,GESTOR,403", "OBSERVADO,EN_REVISION,ADMIN,403",
			"EN_REVISION,POR_PAGAR,GESTOR,200", "EN_REVISION,POR_PAGAR,DUENA,403",
			"EN_REVISION,RECHAZADO,GESTOR,200", "EN_REVISION,RECHAZADO,DUENA,403",
			"POR_PAGAR,FINALIZADO,GESTOR,200", "POR_PAGAR,FINALIZADO,DUENA,403", "POR_PAGAR,FINALIZADO,AUDITOR,403" })
	void cadaTransicionLaHaceSoloQuienDebe(String desde, String hasta, String actor, int esperado) throws Exception {
		when(pagos.tienePagoValidado(anyLong())).thenReturn(true);
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		Map<String, String> tokens = Map.of(
				"DUENA", ana,
				"OTRO_CIUDADANO", tokenCon("luis@demo.com", "CIUDADANO"),
				"GESTOR", tokenCon("gil@demo.com", "GESTOR"),
				"ADMIN", tokenCon("admin@demo.com", "ADMINISTRADOR"),
				"AUDITOR", tokenCon("aud@demo.com", "AUDITOR"));
		long id = crearTramite(ana, DATOS_COMPLETOS);
		ponerEstado(id, desde);

		assertThat(transicionar(tokens.get(actor), id, hasta, "Comentario de prueba").getStatus()).isEqualTo(esperado);
	}

	@Test
	void sinTokenEs401() throws Exception {
		assertThat(transicionar(null, 1, "ENVIADO", null).getStatus()).isEqualTo(401);
		assertThat(enviar(HttpMethod.GET, RUTA + "/1/historial", null, null).getStatus()).isEqualTo(401);
	}

	@Test
	void unEstadoInexistenteEnLaSolicitudEs400() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crearTramite(ana, DATOS_COMPLETOS);

		assertThat(transicionar(ana, id, "INVENTADO", null).getStatus()).isEqualTo(400);
		assertThat(enviar(HttpMethod.POST, RUTA + "/" + id + "/transiciones", ana, "{}").getStatus()).isEqualTo(400);
	}

	@Test
	void unTramiteEliminadoOInexistenteResponde404() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String admin = tokenCon("admin@demo.com", "ADMINISTRADOR");
		long id = crearTramite(ana, DATOS_COMPLETOS);
		enviar(HttpMethod.DELETE, RUTA + "/" + id, admin, null);

		assertThat(transicionar(ana, id, "ENVIADO", null).getStatus()).isEqualTo(404);
		assertThat(transicionar(ana, 999_999L, "ENVIADO", null).getStatus()).isEqualTo(404);
		assertThat(enviar(HttpMethod.GET, RUTA + "/" + id + "/historial", ana, null).getStatus()).isEqualTo(404);
	}

	// ---------- Condiciones de cada transicion ----------

	@Test
	void enviarConDatosIncompletosEs400ConLosCamposQueFaltanYNoCambiaNada() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crearTramite(ana, "{\"ocupacion\":\"Comerciante\"}");

		MockHttpServletResponse r = transicionar(ana, id, "ENVIADO", null);

		assertThat(r.getStatus()).isEqualTo(400);
		List<String> faltan = JsonPath.read(r.getContentAsString(), "$.errores[*].campo");
		assertThat(faltan).containsExactlyInAnyOrder("datos.dpi", "datos.ingreso_mensual");
		assertThat(JsonPath.<String>read(enviar(HttpMethod.GET, RUTA + "/" + id, ana, null).getContentAsString(), "$.estado"))
				.isEqualTo("BORRADOR");
		assertThat(filas("tramite_historial", id)).isEqualTo(1);
		assertThat(filas("bitacora", id)).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "EN_REVISION,OBSERVADO", "EN_REVISION,RECHAZADO" })
	void observarYRechazarExigenComentario(String desde, String hasta) throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String gil = tokenCon("gil@demo.com", "GESTOR");
		long id = crearTramite(ana, DATOS_COMPLETOS);
		ponerEstado(id, desde);

		for (String comentario : new String[] { null, "", "   " }) {
			MockHttpServletResponse r = transicionar(gil, id, hasta, comentario);
			assertThat(r.getStatus()).isEqualTo(400);
			assertThat(JsonPath.<List<String>>read(r.getContentAsString(), "$.errores[*].campo")).contains("comentario");
		}
		assertThat(transicionar(gil, id, hasta, "Falta el comprobante de domicilio").getStatus()).isEqualTo(200);
	}

	@Test
	void finalizarExigeUnPagoValidado() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String gil = tokenCon("gil@demo.com", "GESTOR");
		long id = crearTramite(ana, DATOS_COMPLETOS);
		ponerEstado(id, "POR_PAGAR");

		when(pagos.tienePagoValidado(id)).thenReturn(false);
		assertThat(transicionar(gil, id, "FINALIZADO", null).getStatus()).isEqualTo(409);
		assertThat(JsonPath.<String>read(enviar(HttpMethod.GET, RUTA + "/" + id, gil, null).getContentAsString(), "$.estado"))
				.isEqualTo("POR_PAGAR");

		when(pagos.tienePagoValidado(id)).thenReturn(true);
		MockHttpServletResponse r = transicionar(gil, id, "FINALIZADO", null);
		assertThat(r.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<List<String>>read(r.getContentAsString(), "$.destinosPosibles")).isEmpty();
	}

	@Test
	void pasarAPorPagarExigeQueLosRequisitosEstenCompletos() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String gil = tokenCon("gil@demo.com", "GESTOR");
		long id = crearTramite(ana, DATOS_COMPLETOS);
		ponerEstado(id, "EN_REVISION");
		enviar(HttpMethod.PUT, RUTA + "/" + id, gil, "{\"datos\":{\"ocupacion\":\"Comerciante\"}}");

		MockHttpServletResponse r = transicionar(gil, id, "POR_PAGAR", null);

		assertThat(r.getStatus()).isEqualTo(400);
		assertThat(JsonPath.<List<String>>read(r.getContentAsString(), "$.errores[*].campo")).contains("datos.dpi");
	}

	// ---------- Recorridos completos, historial y bitacora ----------

	@Test
	void unTramiteRecorreTodosSusEstadosConHistorialYBitacora() throws Exception {
		when(pagos.tienePagoValidado(anyLong())).thenReturn(true);
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String gil = tokenCon("gil@demo.com", "GESTOR");
		long idGil = idUsuario(gil);
		long id = crearTramite(ana, "{\"ocupacion\":\"Comerciante\",\"dpi\":\"1234567890123\",\"ingreso_mensual\":2000}");

		assertThat(transicionar(ana, id, "ENVIADO", null).getStatus()).isEqualTo(200);
		MockHttpServletResponse enRevision = transicionar(gil, id, "EN_REVISION", null);
		assertThat(enRevision.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<Number>read(enRevision.getContentAsString(), "$.gestorId").longValue()).isEqualTo(idGil);

		MockHttpServletResponse observado = transicionar(gil, id, "OBSERVADO", "Falta la direccion de residencia");
		assertThat(observado.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<String>read(observado.getContentAsString(), "$.motivo")).isEqualTo("Falta la direccion de residencia");
		String visto = enviar(HttpMethod.GET, RUTA + "/" + id, ana, null).getContentAsString();
		assertThat(JsonPath.<String>read(visto, "$.motivo")).isEqualTo("Falta la direccion de residencia");

		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + id, ana,
				"{\"datos\":{\"ocupacion\":\"Comerciante\",\"dpi\":\"1234567890123\",\"ingreso_mensual\":2000,"
						+ "\"direccion\":\"Zona 1\"}}").getStatus()).isEqualTo(200);
		assertThat(transicionar(ana, id, "EN_REVISION", "Ya agregue la direccion").getStatus()).isEqualTo(200);
		assertThat(transicionar(gil, id, "POR_PAGAR", null).getStatus()).isEqualTo(200);
		MockHttpServletResponse fin = transicionar(gil, id, "FINALIZADO", null);
		assertThat(fin.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<String>read(fin.getContentAsString(), "$.estado")).isEqualTo("FINALIZADO");

		String historial = enviar(HttpMethod.GET, RUTA + "/" + id + "/historial", ana, null).getContentAsString();
		assertThat(JsonPath.<List<String>>read(historial, "$[*].estadoNuevo")).containsExactly(
				"BORRADOR", "ENVIADO", "EN_REVISION", "OBSERVADO", "EN_REVISION", "POR_PAGAR", "FINALIZADO");
		assertThat(JsonPath.<Object>read(historial, "$[0].estadoAnterior")).isNull();
		assertThat(JsonPath.<String>read(historial, "$[2].estadoAnterior")).isEqualTo("ENVIADO");
		assertThat(JsonPath.<String>read(historial, "$[3].comentario")).isEqualTo("Falta la direccion de residencia");
		assertThat(JsonPath.<Number>read(historial, "$[2].actorId").longValue()).isEqualTo(idGil);
		assertThat(JsonPath.<String>read(historial, "$[6].fecha")).isNotBlank();

		assertThat(filas("tramite_historial", id)).isEqualTo(7);
		assertThat(filas("bitacora", id)).isEqualTo(6);
		List<String> acciones = jdbc.queryForList(
				"SELECT accion FROM bitacora WHERE recurso = 'tramite' AND recurso_id = ?", String.class, id);
		assertThat(acciones).containsOnly("TRANSICION_TRAMITE");
		String ultima = jdbc.queryForObject(
				"SELECT antes->>'estado' || '>' || (despues->>'estado') FROM bitacora WHERE recurso_id = ? ORDER BY id DESC LIMIT 1",
				String.class, id);
		assertThat(ultima).isEqualTo("POR_PAGAR>FINALIZADO");
	}

	@Test
	void unRechazoEsDefinitivo() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String gil = tokenCon("gil@demo.com", "GESTOR");
		long id = crearTramite(ana, DATOS_COMPLETOS);
		transicionar(ana, id, "ENVIADO", null);
		transicionar(gil, id, "EN_REVISION", null);

		MockHttpServletResponse r = transicionar(gil, id, "RECHAZADO", "No cumple los requisitos");

		assertThat(r.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<String>read(r.getContentAsString(), "$.motivo")).isEqualTo("No cumple los requisitos");
		assertThat(JsonPath.<List<String>>read(r.getContentAsString(), "$.destinosPosibles")).isEmpty();
		for (String destino : new String[] { "BORRADOR", "ENVIADO", "EN_REVISION", "POR_PAGAR", "FINALIZADO" }) {
			assertThat(transicionar(gil, id, destino, "x").getStatus()).isEqualTo(409);
		}
		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + id, ana, "{\"datos\":{}}").getStatus()).isEqualTo(409);
		assertThat(enviar(HttpMethod.PUT, RUTA + "/" + id, gil, "{\"datos\":{}}").getStatus()).isEqualTo(409);
	}

	@Test
	void elTramiteIndicaAQueEstadosPuedePasar() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crearTramite(ana, DATOS_COMPLETOS);

		assertThat(JsonPath.<List<String>>read(enviar(HttpMethod.GET, RUTA + "/" + id, ana, null).getContentAsString(),
				"$.destinosPosibles")).containsExactly("ENVIADO");
		ponerEstado(id, "EN_REVISION");
		assertThat(JsonPath.<List<String>>read(enviar(HttpMethod.GET, RUTA + "/" + id, ana, null).getContentAsString(),
				"$.destinosPosibles")).containsExactlyInAnyOrder("OBSERVADO", "POR_PAGAR", "RECHAZADO");
	}

	@Test
	void elHistorialLoVeSoloQuienVeElTramite() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		String luis = tokenCon("luis@demo.com", "CIUDADANO");
		String aud = tokenCon("aud@demo.com", "AUDITOR");
		long id = crearTramite(ana, DATOS_COMPLETOS);
		transicionar(ana, id, "ENVIADO", null);

		assertThat(enviar(HttpMethod.GET, RUTA + "/" + id + "/historial", ana, null).getStatus()).isEqualTo(200);
		assertThat(enviar(HttpMethod.GET, RUTA + "/" + id + "/historial", aud, null).getStatus()).isEqualTo(200);
		assertThat(enviar(HttpMethod.GET, RUTA + "/" + id + "/historial", luis, null).getStatus()).isEqualTo(404);
	}

	@Test
	void laCreacionDeUnTramiteQuedaComoPrimeraFilaDelHistorial() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crearTramite(ana, DATOS_COMPLETOS);

		String historial = enviar(HttpMethod.GET, RUTA + "/" + id + "/historial", ana, null).getContentAsString();

		assertThat(JsonPath.<List<Object>>read(historial, "$")).hasSize(1);
		assertThat(JsonPath.<Object>read(historial, "$[0].estadoAnterior")).isNull();
		assertThat(JsonPath.<String>read(historial, "$[0].estadoNuevo")).isEqualTo("BORRADOR");
	}

	// ---------- Inmutabilidad y transaccion ----------

	@Test
	void elHistorialNoSePuedeModificarNiBorrar() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		crearTramite(ana, DATOS_COMPLETOS);
		em.flush();

		assertThatThrownBy(() -> jdbc.update("UPDATE tramite_historial SET comentario = 'alterado'"))
				.rootCause().hasMessageContaining("solo insercion");
	}

	@Test
	void laBitacoraNoSePuedeModificarNiBorrar() throws Exception {
		String ana = tokenCon("ana@demo.com", "CIUDADANO");
		long id = crearTramite(ana, DATOS_COMPLETOS);
		transicionar(ana, id, "ENVIADO", null);
		em.flush();

		assertThatThrownBy(() -> jdbc.update("DELETE FROM bitacora"))
				.rootCause().hasMessageContaining("solo insercion");
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void laBitacoraExigeUnaTransaccionAbiertaParaQueSeGuardeJuntoConLaAccion() {
		assertThatThrownBy(() -> bitacoraService.registrar(1L, "PRUEBA", "tramite", 1L, null, null))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

}
