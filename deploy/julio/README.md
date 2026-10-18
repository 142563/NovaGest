# Entrega integrada: Linux de Julio

Archivos preparados por tres subagentes `gpt-6-luna`, revisados e integrados por el orquestador. No se modifican código Java, CI ni el sitio NavControl existente.

- [Estado observado y decisiones pendientes](ESTADO-VERIFICADO.md).
- [Servidor: auditoría, cuenta personal y Fail2ban](servidor/README.md).
- [Nginx: plantillas privadas y publicación futura](nginx/README.md).
- [Contrato y mensaje para Adrián](COORDINACION-ADRIAN.md).
- [Comprobaciones realizadas y sus límites](VALIDACION.md).

## Copia en Linux

La entrega se coloca en `/home/EJEMPLO/ticas/novagest-despliegue-julio`. Es un directorio de preparación del usuario; sus archivos NO están instalados en `/etc` ni han cambiado servicios. La llave pública del equipo de Julio se copia allí como `julio-acceso.pub`; no contiene material privado. La llave privada permanece en Windows.

## Primer paso con sudo

En la sesión Linux actual, obtener la auditoría protegida:

```bash
cd "$HOME/novagest-despliegue-julio"
umask 077
sudo bash servidor/auditar-servidor.sh > auditoria-protegida.log 2>&1
```

La contraseña sudo se introduce solo en la terminal. El orquestador puede leer después el informe por SSH, sin recibir esa contraseña. La redirección también oculta los mensajes de error; si el comando falla, consultar `tail -n 20 auditoria-protegida.log`.

## Cuenta personal

El script siguiente crea el usuario LOCAL `julio` (distinto de la cuenta de dominio compartida) con sudo y la llave pública. Solo crea una cuenta ausente; no modifica usuarios existentes. Julio define la contraseña del nuevo usuario durante `passwd` para poder usar sudo. No cerrar la sesión original.

```bash
cd "$HOME/novagest-despliegue-julio"
sudo bash servidor/crear-usuario-julio.sh --aplicar --clave-publica "$PWD/julio-acceso.pub"
```

Después, el orquestador prueba `ssh julio@100.87.27.107` con la misma llave. Julio verifica `sudo -v` desde una sesión nueva del usuario `julio`. Esto no completa los accesos del resto del equipo ni rota la contraseña compartida. No se deshabilita autenticación por contraseña hasta verificar todos los administradores.

## Fail2ban

Tras comprobar la cuenta personal y revisar la auditoría, instalar desde una sesión administrativa estable:

```bash
cd /home/EJEMPLO/ticas/novagest-despliegue-julio
sudo bash servidor/instalar-fail2ban.sh --aplicar
```

El script aborta si Fail2ban ya existe; instala una cárcel SSH, comprueba su configuración y servicio y aporta reversión. No habilita UFW; Fail2ban sí puede añadir bloqueos temporales en netfilter. No se ha ejecutado la instalación en esta entrega.

## Nginx

Validación aislada, sin modificar la configuración activa:

```bash
bash nginx/validar-nginx.sh
python3 nginx/probar-proxy.py
```

El segundo comando inicia Nginx temporal y dos backends simulados en loopback y puertos temporales, verifica separación de entornos, conservación de rutas/query, health, cabeceras, saneamiento de XFF y respuesta 429 del límite de login. No valida la aplicación, su autenticación ni sus bases. Al terminar detiene los procesos y elimina temporales.

No habilitar las plantillas todavía: falta el backend real por Adrián, confirmar accesos de red y planificar el arranque de Nginx después de Tailscale cuando se utilice bind a su IP. El dominio público, conectividad entrante y Certbot siguen pendientes de Daniel. Staging público requiere decidir además su control de acceso.

## Qué no está terminado

No se completaron tarjetas del kanban por preparar archivos. Continúan pendientes instalación privilegiada, usuarios del equipo, rotación de cuenta compartida, política UFW que preserve servicios existentes, integración real con backend y datos, publicación pública, renovación TLS y pruebas/evidencias finales.
