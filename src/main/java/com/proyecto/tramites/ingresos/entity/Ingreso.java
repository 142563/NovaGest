package com.proyecto.tramites.ingresos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Tarjeta 19: un ingreso del ciudadano, en quetzales. Es dato personal: solo su dueno lo ve y lo modifica,
 * ni siquiera el administrador. Eliminar lo marca (borrado logico) y queda en la base.
 */
@Entity
@Table(name = "ingreso")
public class Ingreso {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(nullable = false)
	private LocalDate fecha;

	@Column(name = "monto_gtq", nullable = false, precision = 14, scale = 2)
	private BigDecimal montoGtq;

	@Column(length = 200)
	private String descripcion;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@Column(name = "eliminado_en")
	private Instant eliminadoEn;

	protected Ingreso() {
	}

	public Ingreso(Long usuarioId, LocalDate fecha, BigDecimal montoGtq, String descripcion) {
		this.usuarioId = usuarioId;
		this.fecha = fecha;
		this.montoGtq = montoGtq;
		this.descripcion = descripcion;
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

	public void reemplazar(LocalDate fecha, BigDecimal montoGtq, String descripcion) {
		this.fecha = fecha;
		this.montoGtq = montoGtq;
		this.descripcion = descripcion;
	}

	/** Eliminacion logica: el registro queda en la base marcado. */
	public void eliminarLogicamente() {
		eliminadoEn = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public BigDecimal getMontoGtq() {
		return montoGtq;
	}

	public String getDescripcion() {
		return descripcion;
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
