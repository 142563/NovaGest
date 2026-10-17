package com.proyecto.tramites.pagos.dto;

import com.proyecto.tramites.pagos.entity.EstadoPago;
import com.proyecto.tramites.pagos.entity.Pago;
import com.proyecto.tramites.tramites.entity.EstadoTramite;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lo que la API devuelve de un pago. No incluye el nombre del archivo en disco; el comprobante se descarga
 * por GET /api/pagos/{id}/comprobante. tramiteEstado solo viene al crear o resolver un pago.
 */
public record PagoResponse(Long id, Long tramiteId, BigDecimal monto, EstadoPago estado, String comprobanteTipo,
		long comprobanteTamano, String comprobanteHash, Long creadoPor, Long validadoPor, Instant validadoEn,
		String comentario, EstadoTramite tramiteEstado, Instant creadoEn) {

	public static PagoResponse de(Pago pago, EstadoTramite tramiteEstado) {
		return new PagoResponse(pago.getId(), pago.getTramiteId(), pago.getMonto(), pago.getEstado(),
				pago.getComprobanteTipo(), pago.getComprobanteTamano(), pago.getComprobanteHash(), pago.getCreadoPor(),
				pago.getValidadoPor(), pago.getValidadoEn(), pago.getComentario(), tramiteEstado, pago.getCreadoEn());
	}

}
