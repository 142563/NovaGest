package com.proyecto.tramites.auth.repository;

import com.proyecto.tramites.auth.entity.TokenRenovacion;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRenovacionRepository extends JpaRepository<TokenRenovacion, Long> {

	Optional<TokenRenovacion> findByHash(String hash);

	/**
	 * Busca por hash y bloquea la fila: si dos peticiones presentan el mismo token a la vez, la segunda espera
	 * y lo encuentra ya usado, asi un token nunca se canjea dos veces.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from TokenRenovacion t where t.hash = :hash")
	Optional<TokenRenovacion> bloquearPorHash(@Param("hash") String hash);

	/** Revoca de una vez todos los tokens vigentes de un usuario. Devuelve cuantos revoco. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update TokenRenovacion t set t.revocadoEn = :ahora where t.usuario.id = :usuarioId and t.revocadoEn is null")
	int revocarTodosDe(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora);

}
