# Validación de integración — 18 de octubre de 2026

Ejecutada en `daniel1` usando la cuenta sin privilegios `EJEMPLO\ticas`:

- `bash -n` sobre todos los scripts de `servidor/` y `nginx/`: OK.
- `bash nginx/validar-nginx.sh`: Nginx informó sintaxis correcta y prueba satisfactoria.
- `python3 nginx/probar-proxy.py`: OK para separación de producción/staging, conservación de `/api` y query, ruta de salud, cabeceras de protección, descarte de X-Forwarded-For suministrado por el cliente y límite de login con respuesta 429.

Las pruebas usaron backends simulados y una instancia temporal de Nginx, sin sudo. Se terminaron sus procesos y eliminaron sus directorios temporales. No son pruebas funcionales del backend Java ni validación del TLS público futuro.

Copiados los archivos de esta entrega y únicamente la llave **pública** a `/home/EJEMPLO/ticas/novagest-despliegue-julio`. No se instalaron archivos en `/etc`, no se recargó el Nginx activo, no se crearon cuentas ni se instaló Fail2ban. Las plantillas públicas de HTTPS no se validaron con certificados reales porque el dominio está pendiente.

Revisión manual del orquestador: corregidos el estado activo/habilitado en reversión Fail2ban, la propagación de errores para activar reversión, la invocación de scripts sin depender del bit ejecutable, el tratamiento de cabeceras XFF y la descripción del alcance real del límite de peticiones.
