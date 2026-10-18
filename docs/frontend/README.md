# Frontend de NovaGest: plan, reglas y tarjetas

Guia para el equipo y para Claude. Antes de trabajar una tarjeta del front, leer este archivo completo.
Para pedir ayuda: "Trabaja la tarjeta F5 segun docs/frontend/README.md".

Estado: **propuesta**. Los numeros F1 a F13 son provisionales hasta que Ernesto los pase al tablero de Notion;
cuando tengan numero definitivo, actualizar la tabla de abajo (columna "Tablero").

## Decisiones
| Tema | Decision | Por que |
| --- | --- | --- |
| Tecnologia | React + Vite, JavaScript o TypeScript (decidir en F1) | Lo mas documentado; facil de aprender |
| Componentes | Mantine o MUI (decidir en F1) | Formularios, tablas y dialogos ya hechos |
| Ubicacion | Carpeta `frontend/` en este mismo repositorio | Mismo flujo de ramas, PR y revision |
| Despliegue | Contenedor nginx en `docker-compose.yml` que sirve el front y pasa `/api` a la app | El grupo de servidores levanta todo con un solo `docker compose up` |
| CORS | No se configura | Front y API quedan en el mismo origen (`http://servidor/` y `/api`) |
| Permisos | El menu se arma con `GET /api/usuarios/yo` | La API ya devuelve los permisos; el front no decide seguridad |

## Reglas del front (las revisa el grupo de auditoria)
1. Los permisos se validan en la API. El front solo oculta lo que el usuario no puede usar; nunca pregunta por
   el nombre del rol, pregunta por el permiso (`tramite:editar`), igual que el backend.
2. Tokens en memoria (o como minimo `sessionStorage`). Nunca en `localStorage`, nunca en la URL, nunca en logs.
3. Errores: la API responde Problem Details (`title`, `detail`, `errores[{campo, motivo}]`). Mostrar `detail` y,
   en formularios, cada `motivo` junto a su campo. No mostrar trazas ni mensajes internos.
4. Ninguna clave, token ni direccion de produccion escrita en el codigo. La URL base es siempre `/api`.
5. Toda accion que crea, edita o elimina pasa por la API (ella la registra en la bitacora).
6. Textos de la interfaz en espanol.

## Como funciona la sesion (F3)
- `POST /api/auth/login` `{correo, clave}` devuelve `{accessToken, refreshToken, tipo, expiraEnSegundos}`.
  El de acceso dura 15 minutos.
- Cada peticion lleva `Authorization: Bearer <accessToken>`.
- Al recibir 401: llamar una sola vez a `POST /api/auth/refresh` `{refreshToken}`, guardar el par nuevo y repetir
  la peticion. Si el refresh tambien da 401, cerrar sesion y mandar al login. El token de renovacion es de un solo
  uso: si dos peticiones fallan a la vez, que esperen al mismo refresh (no pedir dos).
- Cerrar sesion: `POST /api/auth/logout` `{refreshToken}` y borrar los tokens de memoria.
- Los mensajes de login son genericos ("Credenciales invalidas"); el front no debe intentar dar mas detalle.

## Mapa de pantallas, endpoints y permisos
| Pantalla | Endpoints | Permiso | Tarjeta |
| --- | --- | --- | --- |
| Login / registro | `POST /api/auth/login`, `/registro`, `/refresh`, `/logout` | publico | F3 |
| Perfil y menu | `GET /api/usuarios/yo` | autenticado | F4 |
| Tramites: listado y detalle | `GET /api/tramites` (estado, tipo, fechas, paginado), `GET /api/tramites/{id}`, `GET /api/tramites/{id}/historial` | `tramite:leer` | F5 |
| Tramites: crear y editar | `GET /api/tipos-tramite` (campos del formulario), `POST /api/tramites`, `PUT /api/tramites/{id}` | `tramite:crear`, `tramite:editar` | F5 |
| Pagos del ciudadano | `POST /api/tramites/{id}/pagos` (multipart, campo `comprobante`), `GET /api/pagos`, `GET /api/pagos/{id}` | `pago:crear`, `pago:leer` | F6 |
| Ingresos y proyeccion | `GET/POST/PUT/DELETE /api/ingresos`, `GET /api/ingresos/proyeccion`, `GET /api/divisas/monedas`, `GET /api/divisas/tipo-cambio` | `ingreso:*` | F7 |
| Bandeja del gestor | `GET /api/tramites`, `POST /api/tramites/{id}/transiciones` `{estadoDestino, comentario}` | `tramite:leer`, `tramite:editar` | F8 |
| Validar pagos | `GET /api/pagos`, `GET /api/pagos/{id}/comprobante`, `POST /api/pagos/{id}/validacion` | `pago:leer`, `pago:editar` | F8 |
| Bitacora | `GET /api/bitacora` (recurso, recursoId, accion, actorId, desde, hasta) | `bitacora:leer` | F9 |
| Usuarios | `GET/POST /api/usuarios`, `GET/PUT/DELETE /api/usuarios/{id}`, `POST /api/usuarios/{id}/desbloquear` | `usuario:*` | F10 |
| Roles | `GET/POST /api/roles`, `GET /api/roles/permisos`, `GET/PUT/DELETE /api/roles/{id}` | `rol:*` | F10 |
| Tipos de tramite | `GET/POST /api/tipos-tramite`, `GET/PUT/DELETE /api/tipos-tramite/{id}` | `tipo_tramite:*` | F11 |

Detalle de cada campo: Swagger en `http://localhost:8080/swagger-ui.html` con la app levantada (perfil dev).
Listados paginados: parametros `page`, `size` (maximo 100), `sort`; la respuesta trae `content` y `page`.

Estados de un tramite y quien los mueve (`estadoDestino` en las transiciones):
- Ciudadano (dueno): BORRADOR -> ENVIADO; OBSERVADO -> EN_REVISION.
- Gestor: ENVIADO -> EN_REVISION; EN_REVISION -> OBSERVADO, POR_PAGAR o RECHAZADO; POR_PAGAR -> FINALIZADO.
- FINALIZADO y RECHAZADO son finales. El ciudadano solo edita datos en BORRADOR y OBSERVADO.

## Estructura sugerida de `frontend/`
```
frontend/
  src/
    api/          cliente HTTP (base /api, token, refresh) y una funcion por endpoint
    sesion/       contexto de sesion, login, logout, permisos de /yo
    componentes/  piezas reutilizables (tabla paginada, formulario, mensajes de error)
    paginas/      una carpeta por modulo: tramites/, pagos/, ingresos/, bitacora/, usuarios/, tipos/
    rutas.jsx     rutas protegidas por permiso
  Dockerfile      build + nginx
  nginx.conf      sirve el build y pasa /api a la app
```

## Tarjetas
| ID | Tablero | Tarjeta | Esfuerzo (dias) | Depende de |
| --- | --- | --- | --- | --- |
| F1 | | Esqueleto del frontend | 1 | - |
| F2 | | Front en Docker con nginx | 1 | F1 |
| F3 | | Inicio de sesion, registro y sesion | 2 | F1; tarjetas 8 y 10 |
| F4 | | Estructura de la app y menu por permisos | 1,5 | F3 |
| F5 | | Ciudadano: mis tramites | 3 | F4; tarjetas 15, 16 y 17 |
| F6 | | Ciudadano: pagos y comprobantes | 1,5 | F5; tarjeta 18 |
| F7 | | Ciudadano: ingresos y proyeccion de divisas | 1,5 | F4; tarjetas 19 y 22 |
| F8 | | Gestor: bandeja, cambios de estado y validacion de pagos | 2 | F5; tarjetas 16 y 18 |
| F9 | | Auditor: consulta de bitacora | 1 | F4; tarjeta 23 |
| F10 | | Administrador: usuarios y roles | 2 | F4; tarjeta 12 |
| F11 | | Administrador: tipos de tramite | 1,5 | F4; tarjeta 14 |
| F12 | | Pruebas y revision de seguridad del front | 1,5 | F5 a F11 |
| F13 | | Guia de arranque con el front | 0,5 | F2; tarjeta 24 |

Orden: F1 a F4 primero (una persona o en pareja); despues F5 a F11 en paralelo; F6 y F8 esperan a F5.

### F1 Esqueleto del frontend
- Incluye: proyecto Vite en `frontend/`, libreria de componentes, cliente HTTP con base `/api`, formato y lint,
  proxy de Vite en desarrollo (`/api` -> `http://localhost:8080`) para no necesitar CORS tampoco en local.
- Hecho cuando: `npm run dev` abre la pagina inicial y `frontend/README.md` explica como correrlo.

### F2 Front en Docker con nginx
- Incluye: `frontend/Dockerfile` (build + nginx), `frontend/nginx.conf`, servicio `web` en `docker-compose.yml`
  publicado en el puerto 80; la app deja de publicar el 8080 hacia afuera si el grupo de servidores lo pide.
- Hecho cuando: `docker compose up --build` muestra el front en `http://localhost` y las llamadas a `/api`
  funcionan desde ahi.

### F3 Inicio de sesion, registro y sesion
- Incluye: pantallas de login y registro; manejo de tokens segun "Como funciona la sesion".
- Hecho cuando: los 4 roles entran; pasados 15 minutos la sesion sigue sin pedir clave; al cerrar sesion el
  token deja de servir; los tokens no aparecen en `localStorage` ni en la URL.

### F4 Estructura de la app y menu por permisos
- Incluye: barra y menu, rutas protegidas por permiso, paginas 403 y 404, componente unico para mostrar
  Problem Details.
- Hecho cuando: cada rol ve solo las opciones de sus permisos y una ruta sin permiso muestra la pagina 403.

### F5 Ciudadano: mis tramites
- Incluye: listado con filtros y paginacion, detalle con historial, crear y editar con un formulario que arma
  sus campos segun el tipo de tramite (`campos` de `GET /api/tipos-tramite/{id}`), enviar el tramite.
- Hecho cuando: un ciudadano crea un tramite de cada tipo, lo envia, ve su historial y los errores de
  validacion aparecen junto a cada campo.

### F6 Ciudadano: pagos y comprobantes
- Incluye: pagar un tramite en POR_PAGAR subiendo el comprobante (multipart) y ver el estado del pago.
- Hecho cuando: el ciudadano paga un tramite, sube el comprobante y ve si fue validado o rechazado.

### F7 Ciudadano: ingresos y proyeccion de divisas
- Incluye: CRUD de ingresos propios, pantalla de proyeccion con el tipo de cambio del Banguat.
- Hecho cuando: el ciudadano registra ingresos en varias monedas y ve la proyeccion en quetzales.

### F8 Gestor: bandeja, cambios de estado y validacion de pagos
- Incluye: bandeja de todos los tramites; solo se muestran los botones de transiciones validas desde el estado
  actual (ver "Estados de un tramite"); comentario obligatorio al observar o rechazar; validar pagos viendo
  el comprobante.
- Hecho cuando: un gestor lleva un tramite de ENVIADO a FINALIZADO pasando por la revision y la validacion del pago.

### F9 Auditor: consulta de bitacora
- Incluye: tabla paginada con filtros (recurso, accion, actor, fechas) y detalle antes/despues de cada fila.
- Hecho cuando: el auditor encuentra una accion concreta filtrando y ve quien la hizo y cuando.

### F10 Administrador: usuarios y roles
- Incluye: listado y busqueda de usuarios (filtros `rol`, `activo`, `buscar`), crear, editar roles,
  desactivar, desbloquear y eliminar; roles con su lista de permisos (catalogo en `/api/roles/permisos`).
- Hecho cuando: el administrador crea un gestor, le cambia el rol y lo desactiva, y los 409 de la API
  (por ejemplo, quitarse sus propios roles) se muestran como mensaje claro.

### F11 Administrador: tipos de tramite
- Incluye: CRUD de tipos con el editor de sus campos (clave, etiqueta, tipo de dato, obligatorio, patron,
  minimo, orden).
- Hecho cuando: el administrador crea un tipo con campos y un ciudadano ya puede iniciar un tramite de ese tipo.

### F12 Pruebas y revision de seguridad del front
- Incluye: pruebas automaticas (Vitest + Testing Library) de login, renovacion de sesion y menu por permisos;
  el pipeline de GitHub Actions compila y prueba el front; revisar la lista "Reglas del front".
- Hecho cuando: el pipeline corre las pruebas del front y otra persona firma la revision de las reglas.

### F13 Guia de arranque con el front
- Incluye: sumar el front a la guia de la tarjeta 24 (URL de entrada, usuarios de ejemplo por rol).
- Hecho cuando: un companero nuevo levanta el sistema completo (front + API + base) en 15 minutos con la guia.

## Hecho cuando (para toda tarjeta del front)
- [ ] Compila (`npm run build`) y el pipeline pasa.
- [ ] Probado con un usuario con permiso y uno sin permiso (debe ver 403 o no ver la opcion).
- [ ] Errores de la API mostrados con el componente comun de Problem Details.
- [ ] Cumple las "Reglas del front".
- [ ] Otra persona reviso y aprobo el PR.
