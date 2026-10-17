package com.proyecto.tramites.pagos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro de pago de un tramite con su comprobante adjunto. No mueve dinero: un gestor lo valida o lo
 * rechaza. Quien lo registra no puede resolverlo.
 */
@Entity
@Table(name = "pago")
public class Pago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tramite_id", nullable = false, updatable = false)
	private Long tramiteId;

	/** Dueno del tramite: define quien ve el pago cuando su rol solo tiene alcance sobre lo propio. */
	@Column(name = "ciudadano_id", nullable = false, updatable = false)
	private Long ciudadanoId;

	@Column(nullable = false, updatable = false, precision = 14, scale = 2)
	private BigDecimal monto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoPago estado = EstadoPago.PENDIENTE;

	/** Nombre del archivo en disco, generado por el servidor. Nunca se usa el nombre que mando el cliente. */
	@Column(name = "comprobante_archivo", nullable = false, length = 100, updatable = false)
	private String comprobanteArchivo;

	@Column(name = "comprobante_hash", nullable = false, length = 64, updatable = false)
	private String comprobanteHash;

	@Column(name = "comprobante_tipo", nullable = false, length = 30, updatable = false)
	private String comprobanteTipo;

	@Column(name = "comprobante_tamano", nullable = false, updatable = false)
	private long comprobanteTamano;

	@Column(name = "creado_por", nullable = false, updatable = false)
	private Long creadoPor;

	@Column(name = "validado_por")
	private Long validadoPor;

	@Column(name = "validado_en")
	private Instant validadoEn;

	@Column(length = 500)
	private String comentario;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	protected Pago() {
	}

	public Pago(Long tramiteId, Long ciudadanoId, BigDecimal monto, String comprobanteArchivo,
			String comprobanteHash, String comprobanteTipo, long comprobanteTamano, Long creadoPor) {
		this.tramiteId = tramiteId;
		this.ciudadanoId = ciudadanoId;
		this.monto = monto;
		this.comprobanteArchivo = comprobanteArchivo;
		this.comprobanteHash = comprobanteHash;
		this.comprobanteTipo = comprobanteTipo;
		this.comprobanteTamano = comprobanteTamano;
		this.creadoPor = creadoPor;
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

	public void validar(Long gestorId, String comentario) {
		resolver(EstadoPago.VALIDADO, gestorId, comentario);
	}

	public void rechazar(Long gestorId, String comentario) {
		resolver(EstadoPago.RECHAZADO, gestorId, comentario);
	}

	private void resolver(EstadoPago nuevo, Long gestorId, String comentario) {
		this.estado = nuevo;
		this.validadoPor = gestorId;
		this.validadoEn = Instant.now();
		this.comentario = comentario;
	}

	public Long getId() {
		return id;
	}

	public Long getTramiteId() {
		return tramiteId;
	}

	public Long getCiudadanoId() {
		return ciudadanoId;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public EstadoPago getEstado() {
		return estado;
	}

	public String getComprobanteArchivo() {
		return comprobanteArchivo;
	}

	public String getComprobanteHash() {
		return comprobanteHash;
	}

	public String getComprobanteTipo() {
		return comprobanteTipo;
	}

	public long getComprobanteTamano() {
		return comprobanteTamano;
	}

	public Long getCreadoPor() {
		return creadoPor;
	}

	public Long getValidadoPor() {
		return validadoPor;
	}

	public Instant getValidadoEn() {
		return validadoEn;
	}

	public String getComentario() {
		return comentario;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}

}
