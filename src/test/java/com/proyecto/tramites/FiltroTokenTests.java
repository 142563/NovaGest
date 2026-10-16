package com.proyecto.tramites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Tarjeta 9: el filtro valida el token en cada peticion. Sin token valido 401; sin permiso 403. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FiltroTokenTests {

	private static final String CLAVE = "clave-segura-123";
	private static final String PERFIL = "/api/usuarios/yo";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JwtEncoder codificador;

	private void registrar(String correo) throws Exception {
		String cuerpo = "{\"nombre\":\"Ana Lopez\",\"correo\":\"" + correo + "\",\"clave\":\"" + CLAVE + "\"}";
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/** Registra a un ciudadano, inicia sesion y devuelve su token de acceso. */
	private String tokenDeUnCiudadano(String correo) throws Exception {
		registrar(correo);
		String cuerpo = "{\"correo\":\"" + correo + "\",\"clave\":\"" + CLAVE + "\"}";
		MvcResult resultado = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andReturn();
		return JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
	}

	private int estado(String ruta, String token) throws Exception {
		var peticion = get(ruta);
		if (token != null) {
			peticion = peticion.header("Authorization", "Bearer " + token);
		}
		return mvc.perform(peticion).andReturn().getResponse().getStatus();
	}

	private String cuerpo(String ruta, String token) throws Exception {
		return mvc.perform(get(ruta).header("Authorization", "Bearer " + token)).andReturn().getResponse()
				.getContentAsString();
	}

	private static String firmar(JwtEncoder codificador, String sujeto, Instant emision, Instant vencimiento) {
		JwtClaimsSet datos = JwtClaimsSet.builder().subject(sujeto).issuedAt(emision).expiresAt(vencimiento)
				.id(UUID.randomUUID().toString()).build();
		return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), datos))
				.getTokenValue();
	}

	private String tokenValidoPara(Long idUsuario) {
		Instant ahora = Instant.now();
		return firmar(codificador, String.valueOf(idUsuario), ahora, ahora.plus(15, ChronoUnit.MINUTES));
	}

	@Test
	void sinTokenEs401() throws Exception {
		assertThat(estado(PERFIL, null)).isEqualTo(401);
	}

	@Test
	void conTokenValidoDevuelveElPerfilConSusPermisos() throws Exception {
		String token = tokenDeUnCiudadano("ana@demo.com");

		assertThat(estado(PERFIL, token)).isEqualTo(200);
		String perfil = cuerpo(PERFIL, token);
		assertThat(JsonPath.<String>read(perfil, "$.correo")).isEqualTo("ana@demo.com");
		assertThat(JsonPath.<List<String>>read(perfil, "$.roles")).containsExactly("CIUDADANO");
		assertThat(JsonPath.<List<String>>read(perfil, "$.permisos")).contains("tramite:crear", "ingreso:leer")
				.doesNotContain("usuario:eliminar");
		assertThat(perfil).doesNotContain("hash").doesNotContain("$2");
	}

	@Test
	void unTokenAlteradoEs401() throws Exception {
		String token = tokenDeUnCiudadano("ana@demo.com");
		String[] partes = token.split("\\.");
		char original = partes[1].charAt(10);
		char otro = original == 'A' ? 'B' : 'A';
		String alterado = partes[0] + "." + partes[1].substring(0, 10) + otro + partes[1].substring(11) + "." + partes[2];

		assertThat(estado(PERFIL, alterado)).isEqualTo(401);
	}

	@Test
	void unTokenVencidoEs401() throws Exception {
		tokenDeUnCiudadano("ana@demo.com");
		Usuario ana = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull("ana@demo.com").orElseThrow();
		Instant ahora = Instant.now();
		String vencido = firmar(codificador, String.valueOf(ana.getId()), ahora.minus(20, ChronoUnit.MINUTES),
				ahora.minus(5, ChronoUnit.MINUTES));

		assertThat(estado(PERFIL, vencido)).isEqualTo(401);
	}

	@Test
	void unTokenFirmadoConOtraClaveEs401() throws Exception {
		tokenDeUnCiudadano("ana@demo.com");
		Usuario ana = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull("ana@demo.com").orElseThrow();
		JwtEncoder ajeno = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
				"otra-clave-distinta-de-mas-de-32-caracteres".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		Instant ahora = Instant.now();
		String falso = firmar(ajeno, String.valueOf(ana.getId()), ahora, ahora.plus(15, ChronoUnit.MINUTES));

		assertThat(estado(PERFIL, falso)).isEqualTo(401);
	}

	@Test
	void unEncabezadoSinLaPalabraBearerEs401() throws Exception {
		String token = tokenDeUnCiudadano("ana@demo.com");

		int estado = mvc.perform(get(PERFIL).header("Authorization", token)).andReturn().getResponse().getStatus();

		assertThat(estado).isEqualTo(401);
	}

	@Test
	void unTokenDeUnUsuarioQueNoExisteEs401() throws Exception {
		assertThat(estado(PERFIL, tokenValidoPara(999_999_999L))).isEqualTo(401);
	}

	@Test
	void unTokenConIdNoNumericoEs401() throws Exception {
		Instant ahora = Instant.now();
		String raro = firmar(codificador, "no-es-un-id", ahora, ahora.plus(15, ChronoUnit.MINUTES));

		assertThat(estado(PERFIL, raro)).isEqualTo(401);
	}

	@Test
	void quitarleUnRolAUnUsuarioApliqueDeInmediatoConElMismoToken() throws Exception {
		String token = tokenDeUnCiudadano("ana@demo.com");
		assertThat(estado("/api/_prueba/crear-tramite", token)).isEqualTo(200);

		Usuario ana = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull("ana@demo.com").orElseThrow();
		ana.getRoles().clear();
		usuarios.saveAndFlush(ana);

		assertThat(estado("/api/_prueba/crear-tramite", token)).isEqualTo(403);
		assertThat(JsonPath.<List<String>>read(cuerpo(PERFIL, token), "$.permisos")).isEmpty();
	}

	@Test
	void desactivarAUnUsuarioInvalidaSuTokenDeAcceso() throws Exception {
		String token = tokenDeUnCiudadano("ana@demo.com");
		Usuario ana = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull("ana@demo.com").orElseThrow();
		ana.setActivo(false);
		usuarios.saveAndFlush(ana);

		assertThat(estado(PERFIL, token)).isEqualTo(401);
	}

	@Test
	void eliminarAUnUsuarioInvalidaSuTokenDeAcceso() throws Exception {
		String token = tokenDeUnCiudadano("ana@demo.com");
		Usuario ana = usuarios.findByCorreoIgnoreCaseAndEliminadoEnIsNull("ana@demo.com").orElseThrow();
		ana.eliminarLogicamente();
		usuarios.saveAndFlush(ana);

		assertThat(estado(PERFIL, token)).isEqualTo(401);
	}

	@Test
	void conTokenPeroSinPermisoEs403YConPermisoEs200() throws Exception {
		String token = tokenDeUnCiudadano("ana@demo.com");

		assertThat(estado("/api/_prueba/solo-administrador", token)).isEqualTo(403);
		assertThat(estado("/api/_prueba/crear-tramite", token)).isEqualTo(200);
	}

	@Test
	void sinTokenUnEndpointConPermisoResponde401NoSeSaltaLaSeguridad() throws Exception {
		assertThat(estado("/api/_prueba/solo-administrador", null)).isEqualTo(401);
	}

	@Test
	void unaRutaQueNoExisteSinTokenTambienEs401() throws Exception {
		assertThat(estado("/api/ruta-que-no-existe", null)).isEqualTo(401);
	}

	@Test
	void registroYLoginSiguenSiendoPublicos() throws Exception {
		registrar("ana@demo.com");
		String cuerpo = "{\"correo\":\"ana@demo.com\",\"clave\":\"" + CLAVE + "\"}";

		int estado = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andReturn().getResponse().getStatus();

		assertThat(estado).isEqualTo(200);
	}

}
