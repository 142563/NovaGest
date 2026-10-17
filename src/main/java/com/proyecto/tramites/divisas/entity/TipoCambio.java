package com.proyecto.tramites.divisas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/**
 * Tarjeta 22: un tipo de cambio guardado, uno por fecha y moneda. El valor va tal como lo publica el Banguat
 * (venta); como se interpreta depende de la moneda (ver ConvencionCotizacion). No se edita: se inserta.
 */
@Entity
@Immutable
@Table(name = "tipo_cambio")
public class TipoCambio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@Column(nullable = false, updatable = false)
	private Integer moneda;

	@Column(nullable = false, updatable = false, precision = 18, scale = 6)
	private BigDecimal valor;

	@Column(nullable = false, updatable = false, length = 30)
	private String fuente;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	protected TipoCambio() {
	}

	public TipoCambio(LocalDate fecha, int moneda, BigDecimal valor, String fuente) {
		this.fecha = fecha;
		this.moneda = moneda;
		this.valor = valor;
		this.fuente = fuente;
	}

	@PrePersist
	void alCrear() {
		creadoEn = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public int getMoneda() {
		return moneda;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public String getFuente() {
		return fuente;
	}

}
