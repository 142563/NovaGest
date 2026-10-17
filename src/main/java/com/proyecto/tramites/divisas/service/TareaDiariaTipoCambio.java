package com.proyecto.tramites.divisas.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tarjeta 21: tarea programada que guarda cada dia habil el tipo de cambio de todas las monedas.
 * Horario por defecto: lunes a viernes a las 08:15 (hora de Guatemala); se cambia con
 * app.divisas.tarea-diaria.cron. Se apaga con app.divisas.tarea-diaria.habilitada=false (asi esta en pruebas).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.divisas.tarea-diaria.habilitada", havingValue = "true", matchIfMissing = true)
public class TareaDiariaTipoCambio {

	private final ActualizacionTipoCambioService actualizacion;

	public TareaDiariaTipoCambio(ActualizacionTipoCambioService actualizacion) {
		this.actualizacion = actualizacion;
	}

	@Scheduled(cron = "${app.divisas.tarea-diaria.cron:0 15 8 * * MON-FRI}", zone = "America/Guatemala")
	public void ejecutar() {
		actualizacion.actualizarHoy();
	}

}
