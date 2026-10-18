# Accesos personales al servidor

## Cuentas y alcance

`julio` ya tiene una cuenta local con `sudo`; no modificarla. En el dominio interno de Active Directory ya existen `EJEMPLO\ernesto`, `EJEMPLO\frank` y `EJEMPLO\adrian`. Para evitar ocultar esas identidades mediante NSS, las cuentas locales nuevas usarán el prefijo `ng-`: `ng-ernesto`, `ng-frank` y `ng-brandon` para desarrollo, y `ng-marlon` para administración. Las tres cuentas de desarrollo no tendrán `sudo`, pertenencia a `docker` ni acceso a secretos en `/opt/tramites`. `ng-marlon` tendrá `sudo` en su cuenta personal. No crear `ng-adrian` ni duplicar `EJEMPLO\adrian`; Adrián conserva su cuenta de dominio. El dominio de Active Directory es interno y distinto del dominio público del sitio web.

La pertenencia de `EJEMPLO\adrian` a `domain admins` no confirma que `sudo` funcione en este servidor. Adrián debe comprobarlo desde su propia sesión; no se asume que tenga acceso sudo. Para asignar la contraseña de `ng-marlon`, Marlon debe estar presente y escribirla en una terminal confidencial durante `sudo passwd ng-marlon`. No automatizar ni enviar contraseñas por chat.

Las cuentas locales nuevas se prepararán con la contraseña bloqueada y sin llaves públicas instaladas, así que no están listas para iniciar sesión. No asignar contraseñas predeterminadas ni crear llaves compartidas. El servicio de la aplicación debe ejecutarse con su identidad de servicio dedicada; no usar ni compartir una cuenta humana para ejecutarlo. Las cuentas humanas no tendrán permisos directos sobre secretos de la aplicación. `sudo` en `ng-marlon` implica acceso administrativo elevado y debe usarse solo para tareas autorizadas.

## Preparación de cuentas

`crear-cuentas-equipo.sh` muestra el plan por defecto; `--aplicar` requiere sudo. Se verificó en Linux la sintaxis y el modo plan sin cambios. El script comprueba colisiones locales/NSS, directorios y grupos preexistentes; las cuentas compatibles existentes se conservan sin modificarlas. Las nuevas quedan con contraseña bloqueada, directorio personal propio y `authorized_keys` vacío. Comprueba la autorización efectiva de sudo de desarrollo antes y después de agregar grupos; si detecta privilegios inesperados, falla y retira las membresías que agregó en esa ejecución. Las cuentas creadas se conservan bloqueadas para inspección; no borra usuarios.

```bash
bash /home/julio/novagest-despliegue-julio/servidor/crear-cuentas-equipo.sh --plan
sudo bash /home/julio/novagest-despliegue-julio/servidor/crear-cuentas-equipo.sh --aplicar
```

La creación real requiere la ejecución del segundo comando por Julio. El modo plan no verifica el contenido protegido de sudoers; esa comprobación ocurre con sudo durante la aplicación. El informe final consulta los permisos de Adrián sin modificar su cuenta. La pertenencia a los grupos `novagest-dev` y `novagest-deploy` solo organiza las cuentas: no habilita permisos sobre `/opt/tramites`.

### Recuperación de la primera ejecución

La primera ejecución creó las cuatro cuentas con contraseña bloqueada y archivos de llaves vacíos, pero se detuvo antes de asignar grupos. En este Ubuntu, `sudo-rs` devuelve éxito al listar la denegación de permisos; el script confundió ese código con una autorización. La salida mostrada por Julio confirmó: `User ng-ernesto is not allowed to run sudo on daniel1.`

La comprobación ahora exige ese mensaje explícito con locale C; cualquier autorización, error o salida desconocida detiene la operación. Se probaron las tres situaciones en Bash sin privilegios. Para completar las cuentas ya creadas, sin recrearlas:

```bash
sudo bash /home/julio/novagest-despliegue-julio/servidor/crear-cuentas-equipo.sh --completar-bloqueadas
```

Este modo exige que existan las cuatro cuentas locales previstas, mantengan su contraseña bloqueada, sus directorios pertenezcan a la cuenta y `authorized_keys` sea un archivo propio vacío, sin enlaces simbólicos. Solo añade los grupos pendientes; si falla una verificación posterior retira las membresías añadidas en esta ejecución y conserva las cuentas bloqueadas.

Julio ejecutó la recuperación satisfactoriamente. Se verificaron por SSH las cuentas y sus grupos: `ng-ernesto` (UID 1004), `ng-frank` (1005) y `ng-brandon` (1006) pertenecen a `novagest-dev`, sin autorización sudo según las comprobaciones ejecutadas con root. `ng-marlon` (1007) pertenece a `novagest-deploy` y `sudo`. La captura confirmó que seguían bloqueadas y sin llaves durante la recuperación. La habilitación de acceso SSH de cada integrante sigue pendiente de recibir su llave pública.

El informe omitió a Adrián por un error al interpretar la barra inversa del nombre de dominio; corregido el reconocimiento. Una consulta directa confirmó que `EJEMPLO\adrian` sigue existiendo (UID 3000028); su autorización efectiva de sudo todavía requiere consulta con root. No se creó ni modificó su cuenta.

## Generar y entregar una llave

Cada persona que necesite acceso SSH genera su llave en su propio equipo y protege la privada con una frase de paso, escrita de forma interactiva cuando `ssh-keygen` la solicite. En PowerShell:

```powershell
New-Item -ItemType Directory -Force "$HOME\.ssh" | Out-Null
ssh-keygen -t ed25519 -C 'ng-ernesto-novagest' -f "$HOME\.ssh\id_ed25519_novagest"
Get-Content "$HOME\.ssh\id_ed25519_novagest.pub"
```

Cambiar el comentario por el nombre de la persona (`ng-frank`, `ng-brandon` o `ng-marlon`; Adrián solo si se autoriza llave para su cuenta existente). En Linux o macOS, el equivalente es:

```sh
mkdir -p "$HOME/.ssh"
ssh-keygen -t ed25519 -C 'ng-ernesto-novagest' -f "$HOME/.ssh/id_ed25519_novagest"
cat "$HOME/.ssh/id_ed25519_novagest.pub"
```

Entregar al administrador únicamente el contenido del archivo terminado en `.pub`. La llave privada `id_ed25519_novagest` no se copia al servidor ni se envía a nadie. Un administrador instalará la llave pública después de verificar a quién pertenece y revisar la cuenta correspondiente. Antes de habilitarla, comprobará si ya existe `~/.ssh` o `authorized_keys`; no sobrescribirá archivos ni llaves preexistentes. Revisará propietario y permisos de `.ssh` (`0700`), `authorized_keys` (`0600`) y el directorio personal, y confirmará que no alteró contenido ajeno. Aún no hay un script de instalación para estas cuentas.

Si ya existe `id_ed25519_novagest`, no sobrescribirlo: usar otra ruta o entregar la llave pública existente si corresponde a este acceso.

## Mensaje para el grupo

> Para preparar el acceso personal a Novagest, envíen a Julio únicamente su llave pública Ed25519 (`.pub`), generada en su propio equipo y protegida con frase de paso. Nunca compartan la llave privada ni contraseñas. Las cuentas nuevas se habilitarán cuando un administrador revise la identidad y autorice la llave. El acceso por Tailscale requiere autorización del administrador de Tailscale; no se agregarán redes ni permisos fuera del alcance ya autorizado.
