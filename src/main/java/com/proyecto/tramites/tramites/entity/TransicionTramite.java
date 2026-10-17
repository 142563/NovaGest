package com.proyecto.tramites.tramites.entity;

import static com.proyecto.tramites.tramites.entity.EstadoTramite.BORRADOR;
import static com.proyecto.tramites.tramites.entity.EstadoTramite.ENVIADO;
import static com.proyecto.tramites.tramites.entity.EstadoTramite.EN_REVISION;
import static com.proyecto.tramites.tramites.entity.EstadoTramite.FINALIZADO;
import static com.proyecto.tramites.tramites.entity.EstadoTramite.OBSERVADO;
import static com.proyecto.tramites.tramites.entity.EstadoTramite.POR_PAGAR;
import static com.proyecto.tramites.tramites.entity.EstadoTramite.RECHAZADO;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Las siete transiciones validas de un tramite, y solo ellas. Este enum es el unico lugar donde viven
 * (no hay if repartidos por el codigo). Cada transicion dice desde que estado, hacia cual, quien puede
 * hacerla y que condicion debe cumplirse. Finalizado y Rechazado no tienen salida.
 */
public enum TransicionTramite {

	/** Borrador a Enviado: el ciudadano dueno, con los campos obligatorios completos. */
	ENVIAR(BORRADOR, ENVIADO, Quien.DUENO, false, true, false, false),
	/** Enviado a En revision: un gestor; queda asignado como gestor del tramite. */
	TOMAR_EN_REVISION(ENVIADO, EN_REVISION, Quien.GESTION, false, false, false, true),
	/** En revision a Observado: un gestor, con comentario obligatorio. */
	OBSERVAR(EN_REVISION, OBSERVADO, Quien.GESTION, true, false, false, false),
	/** Observado a En revision: el ciudadano dueno, tras corregir lo observado. */
	CORREGIR(OBSERVADO, EN_REVISION, Quien.DUENO, false, true, false, false),
	/** En revision a Por pagar: un gestor, con los requisitos completos. */
	APROBAR_REVISION(EN_REVISION, POR_PAGAR, Quien.GESTION, false, true, false, false),
	/** En revision a Rechazado: un gestor, con motivo obligatorio. Es definitivo. */
	RECHAZAR(EN_REVISION, RECHAZADO, Quien.GESTION, true, false, false, false),
	/** Por pagar a Finalizado: un gestor, con el pago validado. */
	FINALIZAR(POR_PAGAR, FINALIZADO, Quien.GESTION, false, false, true, false);

	/** Quien puede hacer la transicion. */
	public enum Quien {
		/** Solo el ciudadano dueno del tramite. */
		DUENO,
		/** Personal con alcance sobre todos los tramites (gestor o administrador). */
		GESTION
	}

	private final EstadoTramite desde;
	private final EstadoTramite hasta;
	private final Quien quien;
	private final boolean comentarioObligatorio;
	private final boolean exigeDatosCompletos;
	private final boolean exigePagoValidado;
	private final boolean asignaGestor;

	TransicionTramite(EstadoTramite desde, EstadoTramite hasta, Quien quien, boolean comentarioObligatorio,
			boolean exigeDatosCompletos, boolean exigePagoValidado, boolean asignaGestor) {
		this.desde = desde;
		this.hasta = hasta;
		this.quien = quien;
		this.comentarioObligatorio = comentarioObligatorio;
		this.exigeDatosCompletos = exigeDatosCompletos;
		this.exigePagoValidado = exigePagoValidado;
		this.asignaGestor = asignaGestor;
	}

	/** La transicion que lleva de un estado a otro, si existe. */
	public static Optional<TransicionTramite> buscar(EstadoTramite desde, EstadoTramite hasta) {
		return Arrays.stream(values()).filter(t -> t.desde == desde && t.hasta == hasta).findFirst();
	}

	/** Estados a los que se puede pasar desde uno dado (vacio en los estados finales). */
	public static List<EstadoTramite> destinosDesde(EstadoTramite estado) {
		return Arrays.stream(values()).filter(t -> t.desde == estado).map(t -> t.hasta).toList();
	}

	public EstadoTramite desde() {
		return desde;
	}

	public EstadoTramite hasta() {
		return hasta;
	}

	public Quien quien() {
		return quien;
	}

	public boolean comentarioObligatorio() {
		return comentarioObligatorio;
	}

	public boolean exigeDatosCompletos() {
		return exigeDatosCompletos;
	}

	public boolean exigePagoValidado() {
		return exigePagoValidado;
	}

	public boolean asignaGestor() {
		return asignaGestor;
	}

}
