# Endurecimiento prudente de `daniel1`

Estos archivos preparan la administración del servidor Ubuntu `daniel1`. La auditoría y la creación de la cuenta personal ya fueron ejecutadas por Julio; la conexión por llave al usuario local `julio` se verificó después. Fail2ban sigue pendiente de instalación.

## Contexto operativo confirmado

- Ubuntu Server 26.04.1; Tailscale anuncia `100.87.27.107`.
- La conexión SSH con llave funciona para la cuenta compartida `EJEMPLO\ticas` (UID 3000016); `sudo` pide contraseña.
- La cuenta local personal `julio` existe con UID 1001 y pertenece al grupo `sudo`. Su acceso por llave funciona; la autenticación interactiva de sudo debe comprobarla Julio en su terminal.
- UFW está inactivo. Nginx atiende HTTPS en el puerto 443 para NavControl y su backend escucha en `127.0.0.1:9080`.
- Docker y las actualizaciones desatendidas están activos.
- También están activos `samba-ad-dc`, Postfix, Dovecot y Squid. Este equipo presta servicios de AD, DNS, DHCP, correo y proxy, aunque se había descrito como equipo de Marlon/Windows.
- Wi-Fi `192.168.1.26` está arriba; Ethernet `192.168.50.10` está abajo.

Estos datos son el punto de partida informado, no una auditoría recién ejecutada. Confirma el estado actual antes de aplicar cambios.

## Orden sugerido

1. Desde una sesión administrativa, copia `auditar-servidor.sh` y ejecútalo para guardar una fotografía del estado. El script solo consulta información local.
2. Prepara la cuenta administrativa personal `julio` con el script indicado abajo y verifica una conexión SSH nueva y `sudo` antes de depender de ella. La cuenta compartida dificulta atribuir cambios y un bloqueo afectaría a varias personas. No la modifiques ni la deshabilites durante este paso.
3. Si decides aplicar Fail2ban, ejecuta el instalador desde una sesión estable. No lo combines con cambios de SSH o firewall.
4. Revisa la auditoría y diseña reglas de red con los responsables de cada servicio antes de activar un firewall.

## Fail2ban opcional

`instalar-fail2ban.sh` requiere ejecución explícita como root (`sudo bash instalar-fail2ban.sh --aplicar`). Instala el paquete desde los índices APT ya configurados, agrega una cárcel local solo para SSH y valida la configuración y el estado del servicio antes de habilitarlo explícitamente. No ejecuta `apt update`, no activa UFW ni cambia sus políticas, no altera SSH, no desactiva contraseñas o acceso root y no agrega rangos Tailscale a `ignoreip`. Fail2ban sí puede insertar reglas temporales en netfilter cuando bloquea una dirección; esas reglas runtime forman parte de su funcionamiento.

El instalador coloca una máscara temporal de systemd en `/run` antes de instalar el paquete para impedir el autoarranque durante `apt`. Después de validar la configuración la retira y habilita el servicio. La reversión también retira esa máscara si la operación falla. Los errores al preparar metadatos, antes de comenzar la instalación, eliminan únicamente ese estado parcial para permitir el reintento. Si el paquete ya llegó a instalarse, la reversión lo conserva: un nuevo intento requiere inspección de la instalación existente.

Se selecciona explícitamente `nftables-multiport`. En el paquete Ubuntu `1.1.0-9` inspeccionado, esa acción crea una tabla propia `inet f2b-table` y una cadena de entrada con prioridad `-1`, anterior a la prioridad estándar de filtro `0`. Verificar la configuración efectiva y las reglas tras instalar, sin vaciar ni reemplazar las tablas de Tailscale, Docker o NavControl. La tabla puede no existir hasta el primer bloqueo.

Antes de aplicarlo, confirma desde qué redes y direcciones administran el servidor, que existe acceso alternativo y que la política de intentos fallidos es adecuada para la cuenta compartida. Fail2ban bloquea direcciones de origen; no separa a las personas que usan la misma cuenta. No uses `ignoreip` para toda la red Tailscale sin conocer a sus miembros. El instalador aborta si detecta un paquete, ejecutable, unidad o configuración Fail2ban preexistente, para no reiniciar ni reemplazar jails ajenas.

El instalador se detiene si ya existe su archivo local, para no sobrescribir configuración. Registra el estado previo del servicio en `/var/lib/novagest-hardening/fail2ban/`. Si falla una validación, intenta restaurar la configuración y el estado anterior. Para revertir luego, usa `sudo bash revertir-fail2ban.sh`; el paquete queda instalado para evitar quitar dependencias o cambios de terceros. La reversión detiene o reinicia Fail2ban para que retire o actualice sus reglas temporales y restaura por separado si estaba activo y habilitado. Revisa siempre el resultado y conserva la sesión actual hasta comprobar una nueva conexión SSH.

## Firewall: diseño pendiente

No activar UFW todavía. Hay servicios de infraestructura en la misma máquina y no se conoce el inventario de clientes, interfaces previstas ni las redes autorizadas para AD/DNS, DHCP, correo, proxy, HTTPS, SSH y Tailscale. Autorizar toda la red Tailscale sin confirmar su alcance sería demasiado amplio. La auditoría registra sockets e interfaces para preparar una matriz de origen, interfaz, puerto, protocolo y servicio; la matriz requiere validación del responsable de cada servicio antes de redactar reglas aplicables.

## Auditoría SSH e identidad

No cambiar `PasswordAuthentication`, `PermitRootLogin`, políticas PAM, credenciales ni miembros de grupos hasta confirmar que todas las personas administradoras cuentan con llaves/cuentas separadas y una vía de recuperación probada. La cuenta `EJEMPLO\ticas` está confirmada como compartida; conservarla intacta mientras se prepara y prueba la cuenta personal.

## Archivos

- `auditar-servidor.sh`: consultas de solo lectura sobre SSH, firewall, interfaces, sockets y servicios importantes. Los comandos que leen reglas del kernel pueden necesitar root.
- `instalar-fail2ban.sh`: instalación y configuración opcional, protegida por `--aplicar`.
- `revertir-fail2ban.sh`: elimina solo la cárcel de este paquete y restaura el estado previo de Fail2ban.
- `crear-usuario-julio.sh`: creación opcional de la cuenta administrativa personal `julio`, sin modificar la cuenta compartida.

## Cuenta administrativa personal

`crear-usuario-julio.sh --aplicar --clave-publica /ruta/a/llave.pub` crea el usuario `julio`, lo agrega al grupo `sudo`, instala la llave pública indicada y abre el diálogo interactivo de `passwd`. Julio elige su propia contraseña en esa terminal; el script no recibe ni registra la contraseña. Se aborta si `julio` ya existe, si la llave no se valida o si la sesión no tiene terminal. No deshabilita ni modifica la cuenta compartida. Verifica la nueva conexión SSH y `sudo` antes de planear cualquier migración de acceso.
