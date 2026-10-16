package com.proyecto.tramites.tramites.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Una fila por cada cambio de estado de un tramite. Solo se inserta: ni la aplicacion ni la base la modifican. */
@Entity
@Immutable
@Table(name = "tramite_historial")
public class TramiteHistorial {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tramite_id", nullable = false, updatable = false)
	private Long tramiteId;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado_anterior", length = 20, updatable = false)
	private EstadoTramite estadoAnterior;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado_nuevo", nullable = false, length = 20, updatable = false)
	private EstadoTramite estadoNuevo;

	@Column(name = "actor_id", nullable = false, updatable = false)
	private Long actorId;

	@Column(length = 500, updatable = false)
	private String comentario;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	protected TramiteHistorial() {
	}

	/** El estado anterior es nulo en la primera fila: la creacion del tramite. */
	public TramiteHistorial(Long tramiteId, EstadoTramite estadoAnterior, EstadoTramite estadoNuevo, Long actorId,
			String comentario) {
		this.tramiteId = tramiteId;
		this.estadoAnterior = estadoAnterior;
		this.estadoNuevo = estadoNuevo;
		this.actorId = actorId;
		this.comentario = comentario;
	}

	@PrePersist
	void alCrear() {
		creadoEn = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getTramiteId() {
		return tramiteId;
	}

	public EstadoTramite getEstadoAnterior() {
		return estadoAnterior;
	}

	public EstadoTramite getEstadoNuevo() {
		return estadoNuevo;
	}

	public Long getActorId() {
		return actorId;
	}

	public String getComentario() {
		return comentario;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

}
