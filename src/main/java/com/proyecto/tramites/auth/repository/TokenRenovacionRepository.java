package com.proyecto.tramites.auth.repository;

import com.proyecto.tramites.auth.entity.TokenRenovacion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRenovacionRepository extends JpaRepository<TokenRenovacion, Long> {

	Optional<TokenRenovacion> findByHash(String hash);

}
