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
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Un campo que un tipo de tramite exige o permite en los datos de sus tramites. */
@Entity
@Table(name = "campo_tipo_tramite")
public class CampoTipoTramite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tipo_tramite_id", nullable = false)
	private TipoTramite tipoTramite;

	@Column(nullable = false, length = 50)
	private String clave;

	@Column(nullable = false, length = 100)
	private String etiqueta;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_dato", nullable = false, length = 10)
	private TipoDato tipoDato;

	@Column(nullable = false)
	private boolean obligatorio;

	@Column(length = 200)
	private String patron;

	@Column(precision = 14, scale = 2)
	private BigDecimal minimo;

	@Column(nullable = false)
	private int orden;

	protected CampoTipoTramite() {
	}

	public CampoTipoTramite(TipoTramite tipoTramite) {
		this.tipoTramite = tipoTramite;
	}

	public void definir(String clave, String etiqueta, TipoDato tipoDato, boolean obligatorio, String patron,
			BigDecimal minimo, int orden) {
		this.clave = clave;
		this.etiqueta = etiqueta;
		this.tipoDato = tipoDato;
		this.obligatorio = obligatorio;
		this.patron = patron;
		this.minimo = minimo;
		this.orden = orden;
	}

	public Long getId() {
		return id;
	}

	public String getClave() {
		return clave;
	}

	public String getEtiqueta() {
		return etiqueta;
	}

	public TipoDato getTipoDato() {
		return tipoDato;
	}

	public boolean isObligatorio() {
		return obligatorio;
	}

	public String getPatron() {
		return patron;
	}

	public BigDecimal getMinimo() {
		return minimo;
	}

	public int getOrden() {
		return orden;
	}

}
