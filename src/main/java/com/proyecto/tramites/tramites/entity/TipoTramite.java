package com.proyecto.tramites.tramites.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/** Tipo de tramite (boleto de ornato, pago de IGSS...). Se elimina de forma logica. */
@Entity
@Table(name = "tipo_tramite")
public class TipoTramite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(length = 255)
	private String descripcion;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false)
	private boolean activo = true;

	/** Campos que declara este tipo; los datos de cada tramite se validan contra ellos. */
	@OneToMany(mappedBy = "tipoTramite", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("orden ASC, id ASC")
	@BatchSize(size = 50)
	private List<CampoTipoTramite> campos = new ArrayList<>();

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@Column(name = "eliminado_en")
	private Instant eliminadoEn;

	protected TipoTramite() {
	}

	public TipoTramite(String nombre, String descripcion, BigDecimal monto, boolean activo) {
		actualizar(nombre, descripcion, monto, activo);
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

	public void actualizar(String nombre, String descripcion, BigDecimal monto, boolean activo) {
		this.nombre = nombre;
		this.descripcion = descripcion;
		this.monto = monto;
		this.activo = activo;
	}

	/** El registro queda en la base marcado como eliminado y libera el nombre. */
	public void eliminarLogicamente() {
		eliminadoEn = Instant.now();
		activo = false;
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public boolean isActivo() {
		return activo;
	}

	public List<CampoTipoTramite> getCampos() {
		return campos;
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
