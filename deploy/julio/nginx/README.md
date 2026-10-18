# Nginx para NovaGest

Estas plantillas preparan el proxy del backend Java de NovaGest. La primera etapa da acceso privado por Tailscale. Los dominios para acceso público siguen pendientes.

## Etapa 1: acceso privado por Tailscale

La consulta remota de listeners confirmó libres `8082` y `8083` al preparar esta propuesta. La plantilla enlaza ambos puertos a `100.87.27.107`, la IP Tailscale confirmada por despliegue:

- `100.87.27.107:8082` → backend de producción en `127.0.0.1:8080`.
- `100.87.27.107:8083` → backend de staging en `127.0.0.1:8081`.

Antes de instalar, volver a comprobar en el servidor `ss -lntp` y que `100.87.27.107` siga asignada a la interfaz Tailscale (`tailscale ip -4`). Nginx debe arrancar después de que Tailscale asigne esa IP. El bind a la IP de Tailscale limita la escucha a esa interfaz; el firewall del servidor también debe permitir esos puertos solo desde la red tailnet.

Los backends deben escuchar en loopback, respectivamente en `8080` y `8081`. La configuración de producción y staging de la aplicación debe apuntar a sus bases de datos y secretos propios. No se modifica aquí el servicio existente que ocupa `443`.

`proxy_pass` no incluye una ruta final, por lo que conserva el URI completo: `/api/auth/login` llega a Spring con el prefijo `/api` intacto. `/actuator/health` también se reenvía sin cambios. Las solicitudes al endpoint exacto de login reciben el límite adicional de 10 por minuto por IP, con ráfaga de 5; Spring aplica además el bloqueo por cuenta tras 5 fallos.

Nginx fija `X-Forwarded-For` en la IP de conexión (`$remote_addr`) para no confiar en una cabecera enviada por el cliente. Si se coloca un balanceador delante en el futuro, habrá que configurar explícitamente las IP de proxy confiables antes de usar la IP reenviada.

El límite Nginx de `6m` permite el request multipart completo. Spring limita el archivo individual a `5MB` y el request multipart a `6MB`; para aceptar archivos individuales de 6 MB también habría que ajustar `spring.servlet.multipart.max-file-size` en la aplicación.

Los encabezados añaden protección básica para una API sin frontend. No se inventa una política CSP y no se activa HSTS en esta etapa privada sin TLS.

## Instalación manual

La instalación es manual y queda para el operador Linux. Verificar nuevamente IP y puertos. Copiar los dos fragmentos auxiliares a `/etc/nginx/snippets/` y agregar `novagest-rate-limit.conf` dentro de `http {}` en la configuración principal de Nginx. Copiar `novagest-privado.conf.template` a un archivo temporal, renderizar las tres variables de plantilla con `envsubst`, revisar el resultado y guardarlo en un sitio incluido dentro de `http {}`. Por ejemplo:

```sh
export TAILSCALE_IP=100.87.27.107
export NOVAGEST_PROD_PORT=8082
export NOVAGEST_STAGING_PORT=8083
envsubst '${TAILSCALE_IP} ${NOVAGEST_PROD_PORT} ${NOVAGEST_STAGING_PORT}' \
  < novagest-privado.conf.template > novagest-privado.conf
sudo nginx -t
```

Hacer recarga (`systemctl reload nginx`) solo después de que `nginx -t` pase. Esta tarea solo prepara archivos; no instala ni cambia el servidor.

## Validación local sin privilegios

En Linux con `nginx` y `envsubst` disponibles, ejecutar `bash deploy/julio/nginx/validar-nginx.sh`. El script renderiza la plantilla a `127.0.0.1:18082` y `:18083`, crea pid y log bajo un directorio temporal, ejecuta `nginx -t` y borra el directorio al salir. No necesita sudo y no requiere que la IP Tailscale exista en la máquina de validación. Para seleccionar otro binario: `NGINX_BIN=/ruta/a/nginx bash deploy/julio/nginx/validar-nginx.sh`.

## Futura publicación pública y Certbot

Los dominios aún no están definidos. Los archivos `futuro/novagest-publico-http.conf.template` y `futuro/novagest-publico-https.conf.template` contemplan un dominio para producción y otro para staging. Sustituir `${NOVAGEST_PROD_DOMAIN}` y `${NOVAGEST_STAGING_DOMAIN}` por nombres reales y configurar DNS A/AAAA, firewall y reenvío de puertos 80/443 antes de habilitarlos. El ejemplo HTTP solo permite el desafío ACME y devuelve 404 en otras rutas. El puerto 443 está ocupado actualmente por navcontrol; la plantilla HTTPS es futura y no se puede activar hasta definir cómo se comparte o reasigna ese listener. Esta propuesta no modifica navcontrol.

Después de confirmar que cada dominio resuelve a este servidor y que el puerto 80 es accesible desde Internet, instalar Certbot según la distribución Linux, servir el desafío webroot en `/var/www/certbot` y solicitar el certificado para cada dominio real. La plantilla HTTPS requiere los archivos `fullchain.pem` y `privkey.pem` emitidos por Certbot bajo `/etc/letsencrypt/live/<dominio>/`; no crear certificados ficticios ni activar la plantilla antes de que esos archivos existan. Validar con `nginx -t` y recargar Nginx después de instalar los certificados. Luego, en el HTTP de cada dominio, conservar `location ^~ /.well-known/acme-challenge/` y cambiar el cuerpo de `location /` por `return 301 https://$host$request_uri;`; validar y recargar de nuevo para forzar HTTPS.

## Alcance del backend revisado

La aplicación define `/api/auth/login`, publica `/actuator/health`, y declara archivos de hasta `5MB` con requests multipart de hasta `6MB`. El perfil `prod` desactiva OpenAPI. Estas plantillas no modifican el backend, Docker, el frontend ni el listener 443 existente.
