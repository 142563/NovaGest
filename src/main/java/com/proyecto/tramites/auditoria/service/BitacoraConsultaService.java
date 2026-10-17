package com.proyecto.tramites.auditoria.service;

import com.proyecto.tramites.auditoria.dto.BitacoraResponse;
import com.proyecto.tramites.auditoria.dto.FiltroBitacora;
import com.proyecto.tramites.auditoria.entity.Bitacora;
import com.proyecto.tramites.auditoria.repository.BitacoraEspecificaciones;
import com.proyecto.tramites.auditoria.repository.BitacoraRepository;
import com.proyecto.tramites.comun.util.Actor;
import com.proyecto.tramites.comun.util.Enmascarar;
import com.proyecto.tramites.comun.util.Paginacion;
import com.proyecto.tramites.usuarios.entity.Usuario;
import com.proyecto.tramites.usuarios.repository.UsuarioRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consulta de la bitacora. Solo lectura: la bitacora no se edita ni se borra. Quien la consulta tambien queda
 * registrado (quien miro los registros de auditoria es en si un dato de auditoria).
 */
@Service
public class BitacoraConsultaService {

	private static final Set<String> ORDENABLES = Set.of("id", "creadoEn", "accion", "recurso");
	private static final TypeReference<LinkedHashMap<String, Object>> TIPO_MAPA = new TypeReference<>() {
	};

	private final BitacoraRepository bitacora;
	private final UsuarioRepository usuarios;
	private final BitacoraService registro;
	private final JsonMapper json;

	public BitacoraConsultaService(BitacoraRepository bitacora, UsuarioRepository usuarios, BitacoraService registro,
			JsonMapper json) {
		this.bitacora = bitacora;
		this.usuarios = usuarios;
		this.registro = registro;
		this.json = json;
	}

	/** Pagina de registros, del mas reciente al mas antiguo por defecto. */
	@Transactional
	public Page<BitacoraResponse> consultar(Actor actor, FiltroBitacora filtro, Pageable paginacion) {
		Pageable pagina = Paginacion.validarOrden(paginacion, ORDENABLES);
		Page<Bitacora> filas = bitacora.findAll(BitacoraEspecificaciones.listado(filtro), pagina);
		Map<Long, String> correos = correosDe(filas.getContent());
		Page<BitacoraResponse> respuesta = filas.map(fila -> aRespuesta(fila, correos));
		// La constancia se escribe despues de leer, para que la consulta no aparezca en su propio resultado
		registro.registrar(actor.id(), "CONSULTA_BITACORA", "bitacora", null, null, filtro.resumen());
		return respuesta;
	}

	private Map<Long, String> correosDe(List<Bitacora> filas) {
		Set<Long> ids = new java.util.HashSet<>();
		filas.stream().map(Bitacora::getActorId).filter(Objects::nonNull).forEach(ids::add);
		Map<Long, String> correos = new HashMap<>();
		for (Usuario usuario : usuarios.findAllById(ids)) {
			correos.put(usuario.getId(), Enmascarar.correo(usuario.getCorreo()));
		}
		return correos;
	}

	private BitacoraResponse aRespuesta(Bitacora fila, Map<Long, String> correos) {
		return new BitacoraResponse(fila.getId(), fila.getActorId(), correos.get(fila.getActorId()), fila.getAccion(),
				fila.getRecurso(), fila.getRecursoId(), aMapa(fila.getAntes()), aMapa(fila.getDespues()), fila.getIp(),
				fila.getCreadoEn());
	}

	private Map<String, Object> aMapa(String texto) {
		return texto == null || texto.isBlank() ? null : json.readValue(texto, TIPO_MAPA);
	}

}
