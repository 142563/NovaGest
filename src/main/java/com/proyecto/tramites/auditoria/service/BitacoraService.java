package com.proyecto.tramites.auditoria.service;

import com.proyecto.tramites.auditoria.entity.Bitacora;
import com.proyecto.tramites.auditoria.repository.BitacoraRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe en la bitacora. Exige una transaccion ya abierta (MANDATORY): el registro y la accion que
 * registra se guardan juntos o no se guarda ninguno. Una accion sin bitacora no puede existir.
 */
@Service
public class BitacoraService {

	private final BitacoraRepository bitacora;
	private final JsonMapper json;

	public BitacoraService(BitacoraRepository bitacora, JsonMapper json) {
		this.bitacora = bitacora;
		this.json = json;
	}

	/**
	 * Registra una accion. En antes y despues va solo lo necesario para entender el cambio (por ejemplo el
	 * estado), nunca claves, tokens ni datos personales.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void registrar(Long actorId, String accion, String recurso, Long recursoId,
			Map<String, Object> antes, Map<String, Object> despues) {
		bitacora.saveAndFlush(new Bitacora(actorId, accion, recurso, recursoId, aJson(antes), aJson(despues)));
	}

	private String aJson(Map<String, Object> valores) {
		return valores == null ? null : json.writeValueAsString(valores);
	}

}
