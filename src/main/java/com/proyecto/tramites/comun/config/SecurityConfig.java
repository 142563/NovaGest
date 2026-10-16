package com.proyecto.tramites.comun.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracion base de seguridad. Denegar por defecto: solo registro, login y salud son publicos.
 * La validacion del token en cada peticion (filtro) llega con la tarjeta 9.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	private static final int MINIMO_BYTES_CLAVE = 32;

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** Reloj inyectable: permite probar vencimientos y bloqueos sin esperar. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	SecretKey jwtKey(JwtProperties propiedades) {
		byte[] bytes = propiedades.secret() == null ? new byte[0] : propiedades.secret().getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MINIMO_BYTES_CLAVE) {
			throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres (256 bits)");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
	}

	/** Evita que Spring cree un usuario de arranque con clave aleatoria. Los usuarios reales salen de la base. */
	@Bean
	UserDetailsService sinUsuariosPorDefecto() {
		return correo -> {
			throw new UsernameNotFoundException("Los usuarios se autentican por AuthService");
		};
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.authorizeHttpRequests(rutas -> rutas
						.requestMatchers(HttpMethod.POST, "/api/auth/registro", "/api/auth/login").permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.anyRequest().authenticated());
		return http.build();
	}

}
