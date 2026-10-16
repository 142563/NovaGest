package com.proyecto.tramites.auth.entity;

import com.proyecto.tramites.usuarios.entity.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** Token de renovacion. Solo se guarda el hash SHA-256 del valor entregado al cliente. */
@Entity
@Table(name = "token_renovacion")
public class TokenRenovacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(nullable = false, length = 64, unique = true)
	private String hash;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "vence_en", nullable = false)
	private Instant venceEn;

	@Column(name = "usado_en")
	private Instant usadoEn;

	@Column(name = "revocado_en")
	private Instant revocadoEn;

	protected TokenRenovacion() {
	}

	public TokenRenovacion(Usuario usuario, String hash, Instant venceEn) {
		this.usuario = usuario;
		this.hash = hash;
		this.venceEn = venceEn;
	}

	@PrePersist
	void alCrear() {
		creadoEn = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public String getHash() {
		return hash;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getVenceEn() {
		return venceEn;
	}

	public Instant getUsadoEn() {
		return usadoEn;
	}

	public Instant getRevocadoEn() {
		return revocadoEn;
	}

}
