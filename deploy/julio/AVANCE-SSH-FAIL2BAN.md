# Avance posterior a la primera entrega — 18 de octubre de 2026

- Confirmado por SSH: el usuario local `julio` existe (UID 1001), entra con la llave pública autorizada y pertenece a los grupos `sudo` y `users`.
- La auditoría protegida fue ejecutada por Julio y está en `/home/EJEMPLO/ticas/novagest-despliegue-julio/auditoria-protegida.log`. No se incorpora este informe completo al repositorio.
- Fail2ban `1.1.0-9` instalado por Julio con sudo y verificado después por una conexión SSH nueva: servicio `active` y `enabled`. Python de sistema dispone de `systemd.journal`; la configuración usa ese backend y la acción `nftables-multiport`.
- UFW está inactivo, pero la auditoría sí contiene reglas nftables de Tailscale, Docker y otros servicios. UFW inactivo no significa ausencia de reglas de red.
- Mejorado el instalador para recuperar errores al preparar el estado e impedir el autoarranque durante la instalación del paquete mediante una máscara temporal de systemd.
- Probadas sin root las ramas de recuperación: error previo a la transacción elimina el estado parcial; error durante transacción invoca la reversión; ambas conservan el código de error original.
- Descargado el paquete oficial de Ubuntu `1.1.0-9` y cotejado su SHA256 con los metadatos APT. Extraído en un directorio temporal, sin instalarlo: su cliente aceptó nuestra configuración (`configuration test is successful`). La configuración expandida confirmó journal de `ssh.service`, 5 intentos, ventana/bloqueo de 10 minutos y acción `nftables-multiport` con prioridad `-1`. Se borraron los archivos temporales.

## Instalación realizada y verificación

Julio ejecutó el instalador y mostró su resultado satisfactorio. La máscara temporal fue retirada y la cárcel `sshd` aparece cargada, con 0 direcciones bloqueadas. La conexión nueva por llave a `julio` funciona; Nginx, Docker y Tailscale continúan activos. Hubo un timeout transitorio antes de la comprobación satisfactoria; no se observó evidencia de un bloqueo de Fail2ban. No es necesario volver a ejecutar el instalador.

Consultas para verificar el estado actual:

```bash
systemctl is-active fail2ban
systemctl is-enabled fail2ban
sudo fail2ban-client status sshd
```

La contraseña se introduce únicamente en la terminal. La inserción real de un bloqueo nftables aún no se probó: el estado del servicio y la cárcel no sustituyen esa prueba. Si se ensaya después, usar una IP reservada de documentación, verificar la regla y retirar inmediatamente el bloqueo de prueba. No provocar intentos fallidos desde la IP de administración.

Esta fase no completa toda DES-4: faltan definir/verificar la política de firewall y la evidencia final. Tampoco completa DES-3 para los demás administradores ni cambia la contraseña de la cuenta compartida.
