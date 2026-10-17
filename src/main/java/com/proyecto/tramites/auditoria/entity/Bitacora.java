package com.proyecto.tramites.auditoria.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Immutable;

/**
 * Registro de quien hizo que. Solo se inserta: nadie la edita ni la borra (un trigger de la base lo impide).
 * Los valores antes y despues son JSON con lo minimo necesario; nunca claves ni datos personales.
 */
@Entity
@Immutable
@Table(name = "bitacora")
public class Bitacora {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "actor_id", updatable = false)
	private Long actorId;

	@Column(nullable = false, length = 50, updatable = false)
	private String accion;

	@Column(nullable = false, length = 50, updatable = false)
	private String recurso;

	@Column(name = "recurso_id", updatable = false)
	private Long recursoId;

	@Column(columnDefinition = "jsonb", updatable = false)
	@ColumnTransformer(write = "?::jsonb")
	private String antes;

	@Column(columnDefinition = "jsonb", updatable = false)
	@ColumnTransformer(write = "?::jsonb")
	private String despues;

	@Column(length = 45, updatable = false)
	private String ip;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	protected Bitacora() {
	}

	public Bitacora(Long actorId, String accion, String recurso, Long recursoId, String antes, String despues,
			String ip) {
		this.actorId = actorId;
		this.accion = accion;
		this.recurso = recurso;
		this.recursoId = recursoId;
		this.antes = antes;
		this.despues = despues;
		this.ip = ip;
	}

	@PrePersist
	void alCrear() {
		creadoEn = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getActorId() {
		return actorId;
	}

	public String getAccion() {
		return accion;
	}

	public String getRecurso() {
		return recurso;
	}

	public Long getRecursoId() {
		return recursoId;
	}

	public String getAntes() {
		return antes;
	}

	public String getDespues() {
		return despues;
	}

	public String getIp() {
		return ip;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

}
