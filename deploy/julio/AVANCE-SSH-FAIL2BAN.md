# Avance posterior a la primera entrega — 18 de octubre de 2026

- Confirmado por SSH: el usuario local `julio` existe (UID 1001), entra con la llave pública autorizada y pertenece a los grupos `sudo` y `users`.
- La auditoría protegida fue ejecutada por Julio y está en `/home/EJEMPLO/ticas/novagest-despliegue-julio/auditoria-protegida.log`. No se incorpora este informe completo al repositorio.
- Fail2ban aún no está instalado. APT ofrece `1.1.0-9`; Python de sistema ya dispone del módulo `systemd.journal` y están disponibles nftables e iptables.
- UFW está inactivo, pero la auditoría sí contiene reglas nftables de Tailscale, Docker y otros servicios. UFW inactivo no significa ausencia de reglas de red.
- Mejorado el instalador para recuperar errores al preparar el estado e impedir el autoarranque durante la instalación del paquete mediante una máscara temporal de systemd.
- Probadas sin root las ramas de recuperación: error previo a la transacción elimina el estado parcial; error durante transacción invoca la reversión; ambas conservan el código de error original.
- Descargado el paquete oficial de Ubuntu `1.1.0-9` y cotejado su SHA256 con los metadatos APT. Extraído en un directorio temporal, sin instalarlo: su cliente aceptó nuestra configuración (`configuration test is successful`). La configuración expandida confirmó journal de `ssh.service`, 5 intentos, ventana/bloqueo de 10 minutos y acción `nftables-multiport` con prioridad `-1`. Se borraron los archivos temporales.

## Ejecución pendiente del operador

Conservar abierta la sesión actual. Desde una cuenta con sudo:

```bash
sudo bash /home/julio/novagest-despliegue-julio/servidor/instalar-fail2ban.sh --aplicar
sudo fail2ban-client status sshd
```

La contraseña se introduce únicamente en la terminal. Tras la instalación, comprobar `active`, `enabled`, la cárcel `sshd`, su lectura del journal y las reglas efectivas, además de una nueva conexión SSH por llave. No provocar intentos fallidos desde la IP de administración para probar bloqueos.

Esta fase no completa toda DES-4: faltan definir/verificar la política de firewall y la evidencia final. Tampoco completa DES-3 para los demás administradores ni cambia la contraseña de la cuenta compartida.
