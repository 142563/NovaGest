# Preparación Linux de Julio — 18 de octubre de 2026

## Alcance

Julio: DES-1, DES-2, DES-3, DES-4, DES-6, DES-7 y DES-8. Adrián mantiene los pipelines y despliegues DES-9 a DES-12. Marlon trabaja en Windows Server. No se modificó el backend ni la automatización de Adrián.

## Evidencia observada

- Acceso SSH por llave desde el equipo de Julio a `EJEMPLO\ticas@100.87.27.107`, hostname `daniel1`. La cuenta sigue siendo compartida: no equivale a completar usuarios personales (DES-3).
- Ubuntu 26.04.1 LTS. `sudo` exige autenticación interactiva; la llave SSH no habilita sudo sin contraseña.
- Nginx activo en 443. Sitio existente `navcontrol`, nombres `daniel1.ejemplo.local`, `100.87.27.107` y `192.168.50.10`, proxy a `127.0.0.1:9080` (configuración mostrada por Julio). Preservar este sitio.
- Certificado actual: emisor `EJEMPLO Navigation CA`; CN `daniel1.ejemplo.local`; SAN incluye ese DNS y las IP de Tailscale y Ethernet. Vence el 18 de octubre de 2027. No demuestra un certificado público para NovaGest.
- DNS del servidor resuelve `daniel1.ejemplo.local` a `192.168.50.10`. Interfaz `eno1` sin enlace; salida real por `wlo1`, `192.168.1.26`, gateway `192.168.1.1`.
- UFW inactivo (salida compartida por Julio). SSH efectivo: `permitrootlogin prohibit-password`, `pubkeyauthentication yes`, `passwordauthentication yes`.
- Docker activo. Este usuario no obtuvo información del daemon; no se usó para pruebas.
- Fail2ban y Certbot no figuran instalados en la consulta de paquetes. `unattended-upgrades` activo, configuración periódica habilitada y timer de actualización habilitado.
- `samba-ad-dc`, `postfix`, `dovecot` y `squid` activos. Aunque Marlon trabaje con Windows, Linux también presta servicios de dominio, correo y proxy; no aplicar una política que los corte.
- PostgreSQL escucha en loopback y `192.168.50.10:5432`. No se leyeron credenciales ni se probó la base.
- `/opt/tramites/staging` y `/opt/tramites/production` existen, acceso restringido a deploy. No se inspeccionaron sus archivos `.env`.
- `/usr/local/bin/tramites-ctl` no existe en esa ruta. El kanban dice que la herramienta está instalada; Adrián debe confirmar su ubicación real.
- Puertos TCP 8080, 8081, 8082 y 8083 sin listeners en la consulta inicial; comprobar nuevamente antes de usarlos.

## Decisiones y bloqueos

1. Objetivo final: publicación en internet. Daniel debe proporcionar dominio/subdominios y confirmar conectividad entrante (IP pública, NAT/CGNAT o mecanismo de publicación). No basta con el DNS interno existente.
2. Preparación privada: API producción en loopback 8080, pruebas en loopback 8081; Nginx privado propuesto en Tailscale 8082/8083, sujeto a aprobación de integración y validación. No reemplaza el TLS público final.
3. No habilitar firewall hasta confirmar redes y clientes de los servicios compartidos. No deshabilitar contraseñas SSH hasta probar accesos personales de los administradores.
4. Instalaciones y cambios de configuración privilegiados deben ejecutarse mediante la sesión sudo de Julio. No se creó una excepción NOPASSWD ni se pidió compartir contraseñas.
5. La preparación de archivos no completa las tarjetas: faltan instalación, pruebas reales, evidencia y dominio según corresponda.

## Orden de integración

Auditoría protegida → cuentas personales → Fail2ban → definir política UFW conservando servicios → despliegue backend por Adrián → Nginx privado y pruebas → dominio/conectividad pública → HTTPS y renovación → límites/cabeceras y evidencia final.
