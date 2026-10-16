package com.proyecto.tramites.tramites.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Un tramite de un ciudadano. El estado no se edita como un campo mas: lo cambian las transiciones
 * (tarjeta 16). Los datos propios del tipo se guardan como JSON en la columna datos (jsonb).
 */
@Entity
@Table(name = "tramite")
public class Tramite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tipo_tramite_id", nullable = false)
	private TipoTramite tipoTramite;

	@Column(name = "ciudadano_id", nullable = false, updatable = false)
	private Long ciudadanoId;

	@Column(name = "gestor_id")
	private Long gestorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoTramite estado = EstadoTramite.BORRADOR;

	/** JSON con los datos del tramite. Se escribe como jsonb; se lee y se escribe con ValidadorDatosTramite. */
	@Column(nullable = false, columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	private String datos = "{}";

	@Column(length = 500)
	private String motivo;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@Column(name = "eliminado_en")
	private Instant eliminadoEn;

	protected Tramite() {
	}

	public Tramite(TipoTramite tipoTramite, Long ciudadanoId, String datos) {
		this.tipoTramite = tipoTramite;
		this.ciudadanoId = ciudadanoId;
		this.datos = datos;
	}

	@PrePersist
	void alCrear() {
		creadoEn = Instant.now();
		actualizadoEn = creadoEn;
	}

	@PreUpdate
	void alActualizar() {
		actualizadoEn = Instant.now();
	}

	public void reemplazarDatos(String datos) {
		this.datos = datos;
	}

	/** Eliminacion logica: el registro queda en la base marcado. */
	public void eliminarLogicamente() {
		eliminadoEn = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public TipoTramite getTipoTramite() {
		return tipoTramite;
	}

	public Long getCiudadanoId() {
		return ciudadanoId;
	}

	public Long getGestorId() {
		return gestorId;
	}

	public EstadoTramite getEstado() {
		return estado;
	}

	public String getDatos() {
		return datos;
	}

	public String getMotivo() {
		return motivo;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}

	public Instant getEliminadoEn() {
		return eliminadoEn;
	}

}
