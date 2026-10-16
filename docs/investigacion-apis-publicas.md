# Investigacion de APIs publicas para NovaGest

Fecha de las pruebas: 15 de octubre de 2026. Todas las consultas fueron de solo lectura y se hicieron desde el equipo de desarrollo. Las respuestas del Banguat estan guardadas en `docs/banguat/muestras/` para usarlas como respuestas grabadas en las pruebas (WireMock), tal como pide el plan.

## Resumen: que sirve y que no

| API | Para que serviria en NovaGest | Llave | Condiciones | Resultado de la prueba | Recomendacion |
| --- | --- | --- | --- | --- | --- |
| Banguat, tipo de cambio (SOAP) | Proyeccion de ingresos en otras monedas (ya esta en el plan) | No | Servicio publico del Banco de Guatemala | Funciona. 8 operaciones, 40 monedas | Usar. Es la fuente oficial |
| open.er-api.com | Respaldo cuando el Banguat no responde; segunda fuente para contrastar | No | Gratis; 1 actualizacion al dia; atribucion obligatoria ("Rates By Exchange Rate API"); si se abusa responde 429 por 20 minutos; no se pueden redistribuir los datos | Funciona. 166 monedas, incluye GTQ | Usar solo como respaldo marcado como estimado, con atribucion |
| Nager.Date (feriados) | Calendario de dias habiles: fecha limite de un tramite, dia habil siguiente | No | Codigo abierto; el sitio anuncia "sin limites"; no indica licencia ni atribucion en su pagina | Funciona (v3). 13 feriados de Guatemala en 2026. El sitio ya anuncia una v4 en nagerholidays.com | Usar con cache. Datos de terceros: no cubren feriados locales. Confirmar fechas con fuentes oficiales antes de usarlas en plazos legales |
| World Bank | Indicadores del pais (poblacion, PIB) para reportes o un tablero | No | Publica y gratuita | Funciona (poblacion de Guatemala 2025: 18,687,881) | Opcional. Util para contexto, no para el flujo de tramites |
| Open-Meteo (clima) | Ninguno claro para tramites | No | Gratis para uso no comercial | Funciona | No recomendada |
| Zippopotam (codigos postales) | Autocompletar zona o departamento en direcciones | No | Servicio de un tercero, sin garantias | Funciona, pero cubre solo zonas de la Ciudad de Guatemala en la prueba | No recomendada como dependencia |
| Frankfurter | Otra fuente de tipo de cambio | No | Gratis, datos del BCE | Funciona, pero solo 30 monedas y **no incluye GTQ** | Descartada |
| REST Countries | Datos de paises | **Si, desde la v5** | La version que usaba cada tutorial (v3.1) esta dada de baja; la nueva exige cuenta y llave | La version anterior responde con error de deprecacion | Descartada |
| datos.gob.gt | Datos abiertos del Estado | No verificado | Portal web | El portal responde, pero no se encontro una API estandar (`/api/3` da 404) | Explorar a mano; no integrar |
| SAT (consulta CUI y NIT), RENAP, Registro Mercantil | Validar DPI, NIT o empresas | n/d | Son portales web; en la busqueda no aparecio una API publica documentada | No se probo; no hay API que probar | No integrar ni extraer datos de esas paginas. Validar solo el formato en el propio sistema |

## Hallazgo importante: como expresa el Banguat las otras monedas

El plan dice: "antes de programar otras monedas hay que confirmar como viene expresado el valor". Se consultaron 10 monedas. El resultado es que **el Banguat usa dos convenciones segun la moneda**.

| Moneda (codigo Banguat) | Valor del 02/10/2026 | Como se lee |
| --- | --- | --- |
| Dolar (2) | 7.64136 | Quetzales por 1 dolar |
| Yen (3) | 157.52 | Yenes por 1 dolar |
| Dolar canadiense (7) | 1.4235 | Dolares canadienses por 1 dolar |
| Franco suizo (5) | 0.8273 | Francos por 1 dolar |
| Peso mexicano (18) | 18.1716 | Pesos por 1 dolar |
| Colon costarricense (16) | 462.29 | Colones por 1 dolar |
| Real brasileno (30) | 5.2309 | Reales por 1 dolar |
| Peso colombiano (38) | 3277.55 | Pesos por 1 dolar |
| Lempira (19) | 27.027 | Lempiras por 1 dolar |
| **Euro (24)** | **1.1275** | **Dolares por 1 euro** |
| **Libra esterlina (9)** | **1.324** | **Dolares por 1 libra** |

Se comprobo el euro y la libra contra una segunda fuente (open.er-api.com): 1 dolar = 0.89165 euros, es decir 1 euro = 1.1215 dolares, cerca del 1.1275 del Banguat (la diferencia es de horario y fuente). Para la libra pasa lo mismo. Las demas monedas coinciden en orden de magnitud con "unidades por dolar".

**Consecuencia para las tarjetas 20 a 22:** no se puede aplicar una sola formula. Cada moneda necesita guardar como se cotiza:

- Si se cotiza como **unidades por dolar** (la mayoria): `monto en moneda X = (quetzales / tipo de cambio del dolar) x valor de la moneda X`.
- Si se cotiza como **dolares por unidad** (euro, libra y probablemente DEG): `monto en moneda X = (quetzales / tipo de cambio del dolar) / valor de la moneda X`.

Ejemplo con Q3,000 el 02/10/2026: 3000 / 7.64136 = 392.60 dolares; en euros: 392.60 / 1.1275 = 348.2 euros; en yenes: 392.60 x 157.52 = 61,840 yenes.

Limites de esta prueba: solo se verificaron 10 de las 40 monedas. Los casos DEG (26), Unidad de Cuenta del BID (28) y las monedas que ya no existen (francos belgas, marcos alemanes, liras, pesetas, etc.) no se verificaron. Para el selector de monedas conviene mostrar solo las vigentes y confirmar cada una contra una segunda fuente antes de incluirla.

## Detalles tecnicos del servicio del Banguat

- Direccion: `https://www.banguat.gob.gt/variables/ws/TipoCambio.asmx` (SOAP 1.1). Espacio de nombres: `http://www.banguat.gob.gt/variables/ws/`. El encabezado `SOAPAction` es el espacio de nombres mas el nombre de la operacion, entre comillas.
- Operaciones: `VariablesDisponibles`, `Variables`, `TipoCambioDia`, `TipoCambioDiaString`, `TipoCambioFechaInicial`, `TipoCambioFechaInicialMoneda`, `TipoCambioRango` y `TipoCambioRangoMoneda`.
- Parametros de `TipoCambioRangoMoneda`: `fechainit` y `fechafin` (texto con formato `dd/MM/yyyy`) y `moneda` (entero con el codigo).
- `TipoCambioDia` devuelve un solo valor (`fecha` y `referencia`) del dolar.
- `TipoCambioRango` (dolar) incluye tambien sabados y domingos, repitiendo el ultimo valor; `TipoCambioRangoMoneda` (otras monedas) **omite** los dias sin cotizacion (el 03 y 04 de octubre de 2026 no aparecen). Para un ingreso de un fin de semana hay que usar el ultimo dia habil anterior.
- Cada fila trae `venta` y `compra`. En el dolar coinciden (valor de referencia); en otras monedas pueden diferir (colon: 462.29 y 456.56).
- Tiempo de respuesta observado: cerca de 0.5 segundos.

## Nuevas consultas que podria ofrecer el sistema

Orden sugerido segun valor y esfuerzo:

1. **Divisas** (ya en el plan, tarjetas 20 a 22): `GET /api/divisas/monedas` y `GET /api/divisas/tipo-cambio`.
2. **Calendario de dias habiles**: `GET /api/calendario/feriados?anio=2026` y una consulta de "siguiente dia habil". Sirve para calcular fechas limite de tramites y para el tipo de cambio de fines de semana.
3. **Tipo de cambio de respaldo**: si el Banguat no responde y no hay valor guardado, consultar open.er-api.com y marcar la respuesta como estimada, con la fuente y la atribucion.
4. **Indicadores del pais** (World Bank): solo si sobra tiempo; da contexto, no resuelve un tramite.

## Reglas para integrar cualquier API externa

- Un cliente propio por servicio (como `BanguatClient`): el resto del codigo recibe objetos Java, nunca XML ni JSON de afuera.
- Tiempo de espera de 5 segundos, dos reintentos y cache en la base. Si el servicio cae, se responde con el ultimo valor guardado marcado como estimado.
- La respuesta siempre dice la fuente y la fecha del dato.
- Las pruebas usan respuestas grabadas, nunca el servicio en vivo.
- No se envian datos personales a servicios de terceros: solo consultas de datos publicos (fechas, monedas, paises).
- Respetar atribucion y limites de cada servicio (open.er-api.com exige atribucion).
- Entregar a Despliegue la lista de dominios externos (fase 5 del plan): `www.banguat.gob.gt`, `open.er-api.com`, `date.nager.at` y, si se usa, `api.worldbank.org`.
- Control de auditoria sugerido: inventario de integraciones con terceros (que datos salen, hacia donde y con que justificacion).
