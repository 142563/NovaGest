package com.proyecto.tramites.tramites.service;

import com.proyecto.tramites.auditoria.service.BitacoraService;
import com.proyecto.tramites.comun.excepcion.DatosInvalidosException;
import com.proyecto.tramites.comun.util.Actor;
import com.proyecto.tramites.comun.util.Paginacion;
import com.proyecto.tramites.tramites.dto.CampoTipoRequest;
import com.proyecto.tramites.tramites.dto.TipoTramiteRequest;
import com.proyecto.tramites.tramites.dto.TipoTramiteResponse;
import com.proyecto.tramites.tramites.entity.CampoTipoTramite;
import com.proyecto.tramites.tramites.entity.TipoDato;
import com.proyecto.tramites.tramites.entity.TipoTramite;
import com.proyecto.tramites.tramites.repository.TipoTramiteRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reglas de los tipos de tramite. Quien puede editar (tipo_tramite:editar) ve tambien los inactivos;
 * los demas solo ven los activos y piden uno inactivo como si no existiera (404).
 * Cada tipo declara los campos que valida los datos de sus tramites.
 */
@Service
public class TipoTramiteService {

	private static final Set<String> ORDENABLES = Set.of("nombre", "monto", "creadoEn");

	private final TipoTramiteRepository tipos;
	private final BitacoraService bitacora;

	public TipoTramiteService(TipoTramiteRepository tipos, BitacoraService bitacora) {
		this.tipos = tipos;
		this.bitacora = bitacora;
	}

	@Transactional(readOnly = true)
	public Page<TipoTramiteResponse> listar(boolean administra, Boolean activo, Pageable paginacion) {
		Pageable pagina = Paginacion.validarOrden(paginacion, ORDENABLES);
		Boolean filtro = administra ? activo : Boolean.TRUE;
		Page<TipoTramite> resultado = filtro == null
				? tipos.findByEliminadoEnIsNull(pagina)
				: tipos.findByEliminadoEnIsNullAndActivo(filtro, pagina);
		return resultado.map(TipoTramiteResponse::de);
	}

	@Transactional(readOnly = true)
	public TipoTramiteResponse obtener(Long id, boolean administra) {
		return TipoTramiteResponse.de(buscar(id, administra));
	}

	@Transactional
	public TipoTramiteResponse crear(Actor actor, TipoTramiteRequest solicitud) {
		String nombre = solicitud.nombre().trim();
		if (tipos.existsByNombreIgnoreCaseAndEliminadoEnIsNull(nombre)) {
			throw nombreEnUso();
		}
		TipoTramite tipo = new TipoTramite(nombre, limpiar(solicitud.descripcion()), solicitud.monto(),
				solicitud.activoONulo());
		if (solicitud.campos() != null) {
			sincronizarCampos(tipo, solicitud.campos());
		}
		TipoTramite guardado = tipos.saveAndFlush(tipo);
		bitacora.registrar(actor.id(), "CREAR_TIPO_TRAMITE", "tipo_tramite", guardado.getId(), null, resumen(guardado));
		return TipoTramiteResponse.de(guardado);
	}

	@Transactional
	public TipoTramiteResponse actualizar(Actor actor, Long id, TipoTramiteRequest solicitud) {
		TipoTramite tipo = buscar(id, true);
		Map<String, Object> antes = resumen(tipo);
		String nombre = solicitud.nombre().trim();
		if (tipos.existsByNombreIgnoreCaseAndEliminadoEnIsNullAndIdNot(nombre, id)) {
			throw nombreEnUso();
		}
		tipo.actualizar(nombre, limpiar(solicitud.descripcion()), solicitud.monto(), solicitud.activoONulo());
		if (solicitud.campos() != null) {
			sincronizarCampos(tipo, solicitud.campos());
		}
		TipoTramite guardado = tipos.saveAndFlush(tipo);
		bitacora.registrar(actor.id(), "EDITAR_TIPO_TRAMITE", "tipo_tramite", guardado.getId(), antes, resumen(guardado));
		return TipoTramiteResponse.de(guardado);
	}

	/** Eliminacion logica: el registro se marca y queda en la base. */
	@Transactional
	public void eliminar(Actor actor, Long id) {
		TipoTramite tipo = buscar(id, true);
		Map<String, Object> antes = resumen(tipo);
		tipo.eliminarLogicamente();
		tipos.saveAndFlush(tipo);
		bitacora.registrar(actor.id(), "ELIMINAR_TIPO_TRAMITE", "tipo_tramite", tipo.getId(), antes,
				Map.of("eliminado", true));
	}

	/** Lo que queda en la bitacora de un tipo: nombre, monto, si esta activo y cuantos campos declara. */
	private static Map<String, Object> resumen(TipoTramite tipo) {
		Map<String, Object> resumen = new LinkedHashMap<>();
		resumen.put("nombre", tipo.getNombre());
		resumen.put("monto", tipo.getMonto());
		resumen.put("activo", tipo.isActivo());
		resumen.put("campos", tipo.getCampos().size());
		return resumen;
	}

	/**
	 * Deja en el tipo exactamente los campos pedidos. Se actualizan los que ya existen (misma clave),
	 * se crean los nuevos y se quitan los que ya no estan; asi no choca la clave unica de la base.
	 */
	private void sincronizarCampos(TipoTramite tipo, List<CampoTipoRequest> pedidos) {
		validarCampos(pedidos);
		Map<String, CampoTipoTramite> actuales = new HashMap<>();
		tipo.getCampos().forEach(campo -> actuales.put(campo.getClave(), campo));
		Set<String> clavesPedidas = new HashSet<>();
		pedidos.forEach(pedido -> clavesPedidas.add(pedido.clave()));
		tipo.getCampos().removeIf(campo -> !clavesPedidas.contains(campo.getClave()));
		int posicion = 0;
		for (CampoTipoRequest pedido : pedidos) {
			CampoTipoTramite campo = actuales.get(pedido.clave());
			if (campo == null) {
				campo = new CampoTipoTramite(tipo);
				tipo.getCampos().add(campo);
			}
			campo.definir(pedido.clave(), pedido.etiqueta().trim(), pedido.tipoDato(), pedido.obligatorioONulo(),
					limpiar(pedido.patron()), pedido.minimo(), pedido.orden() != null ? pedido.orden() : posicion);
			posicion++;
		}
	}

	private static void validarCampos(List<CampoTipoRequest> pedidos) {
		DatosInvalidosException.Acumulador errores = new DatosInvalidosException.Acumulador();
		Set<String> vistas = new HashSet<>();
		for (int i = 0; i < pedidos.size(); i++) {
			CampoTipoRequest pedido = pedidos.get(i);
			String ruta = "campos[" + i + "]";
			if (!vistas.add(pedido.clave())) {
				errores.agregar(ruta + ".clave", "La clave esta repetida en el tipo");
			}
			String patron = limpiar(pedido.patron());
			if (patron != null && pedido.tipoDato() != TipoDato.TEXTO) {
				errores.agregar(ruta + ".patron", "El patron solo aplica a campos de tipo TEXTO");
			}
			else if (patron != null) {
				try {
					Pattern.compile(patron);
				}
				catch (PatternSyntaxException e) {
					errores.agregar(ruta + ".patron", "No es una expresion regular valida");
				}
			}
			if (pedido.minimo() != null && pedido.tipoDato() != TipoDato.NUMERO) {
				errores.agregar(ruta + ".minimo", "El minimo solo aplica a campos de tipo NUMERO");
			}
		}
		errores.lanzarSiHayErrores();
	}

	private TipoTramite buscar(Long id, boolean administra) {
		return tipos.findByIdAndEliminadoEnIsNull(id)
				.filter(tipo -> administra || tipo.isActivo())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El tipo de tramite no existe"));
	}

	private static String limpiar(String texto) {
		return texto == null || texto.isBlank() ? null : texto.trim();
	}

	private static ResponseStatusException nombreEnUso() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un tipo de tramite con ese nombre");
	}

}
