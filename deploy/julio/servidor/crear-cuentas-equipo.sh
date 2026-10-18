#!/usr/bin/env bash
set -Eeuo pipefail

readonly SCRIPT_NAME=${0##*/}
readonly DEV_GROUP=novagest-dev
readonly DEPLOY_GROUP=novagest-deploy
readonly SUDO_GROUP=sudo
readonly -a DEV_USERS=(ng-ernesto ng-frank ng-brandon)
readonly -a ALL_USERS=(ng-ernesto ng-frank ng-brandon ng-marlon)

mode=plan

usage() {
  cat <<EOF
Uso:
  bash $SCRIPT_NAME [--plan]
  sudo bash $SCRIPT_NAME --aplicar

Por defecto solo muestra el plan. --aplicar requiere root y crea estas cuentas
locales sin contraseña ni llaves SSH:
  ng-ernesto, ng-frank y ng-brandon: grupo $DEV_GROUP, sin sudo.
  ng-marlon: grupos $DEPLOY_GROUP y $SUDO_GROUP.

Las cuentas usan /bin/bash y /home/<usuario>. Sus claves SSH autorizadas quedan
vacías. El script no asigna grupos docker ni concede permisos sobre /opt/tramites
o archivos de entorno. No crea ni modifica cuentas preexistentes. Adrián se
omite para conservar intacta su posible cuenta de dominio.
EOF
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

declare -a created_users=()
declare -a added_memberships=()
attempting_user=''
applying=false

on_exit() {
  local rc=$?
  trap - EXIT
  if [[ "$applying" == true && $rc -ne 0 ]]; then
    local i pair member group
    for ((i=${#added_memberships[@]}-1; i>=0; i--)); do
      pair=${added_memberships[i]}
      member=${pair%%:*}
      group=${pair#*:}
      if ! gpasswd -d "$member" "$group" >/dev/null 2>&1; then
        printf 'AVISO: no se pudo retirar el grupo %s de %s; verifica esa membresía manualmente.\n' "$group" "$member" >&2
      fi
    done
    if [[ -n "$attempting_user" ]]; then
      printf 'ERROR: falló la preparación de %s. La cuenta podría existir de forma parcial; no se elimina ni se recrea.\n' "$attempting_user" >&2
    fi
    if (( ${#created_users[@]} > 0 )); then
      printf 'Cuentas creadas que se conservaron: %s. No se asignaron privilegios restantes.\n' "${created_users[*]}" >&2
    fi
  fi
  exit "$rc"
}
trap on_exit EXIT

(( $# <= 1 )) || die 'Usa solo --plan o --aplicar.'
while (($#)); do
  case "$1" in
    --plan)
      [[ "$mode" == plan ]] || die 'No combines --plan con --aplicar.'
      mode=plan
      ;;
    --aplicar)
      [[ "$mode" == plan ]] || die 'No combines --plan con --aplicar.'
      mode=apply
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      die "Opción desconocida: $1"
      ;;
  esac
  shift
done

local_passwd_line() {
  awk -F: -v name="$1" '$1 == name { print; found=1 } END { if (!found) exit 1 }' /etc/passwd
}

local_group_line() {
  awk -F: -v name="$1" '$1 == name { print; found=1 } END { if (!found) exit 1 }' /etc/group
}

# Detecta nombres cortos y cuentas con prefijo de dominio o sufijo UPN.
nss_user_matches() {
  { getent passwd "$1" || true; getent passwd || true; } | awk -F: -v name="${1,,}" '{
    login = tolower($1)
    sub(/^.*\\\\/, "", login)
    sub(/@.*$/, "", login)
    if (login == name) print $1
  }' | sort -u
}

preflight_user() {
  local user=$1 line passwd_field uid home shell external
  if line=$(local_passwd_line "$user"); then
    IFS=: read -r passwd_field _ uid _ _ home shell <<< "$line"
    [[ "$uid" =~ ^[0-9]+$ ]] || die "UID inválido para la cuenta local $user."
    ((uid >= 1000)) || die "La cuenta local $user existe con UID $uid; se esperaba UID >= 1000."
    [[ "$home" == "/home/$user" ]] || die "La cuenta local $user existe con home $home; se esperaba /home/$user."
    [[ "$shell" == /bin/bash ]] || die "La cuenta local $user existe con shell $shell; se esperaba /bin/bash."
    external=$(nss_user_matches "$user" | awk -F: -v local="$user" '$1 != local { print $1 }')
    [[ -z "$external" ]] || die "Hay otra identidad NSS que colisiona con $user: $external. No se modificará ninguna cuenta."
    printf 'EXISTE local compatible: %s (UID %s, home %s, shell %s); no se modificará.\n' "$user" "$uid" "$home" "$shell"
  else
    external=$(nss_user_matches "$user")
    [[ -z "$external" ]] || die "El nombre $user ya resuelve en NSS ($external), pero no es una cuenta local; no crearé una cuenta que la oculte."
    [[ ! -e "/home/$user" && ! -L "/home/$user" ]] || die "Ya existe /home/$user; no modificaré ese directorio ni un enlace preexistente."
    [[ -z "$(getent group "$user" || true)" ]] || die "Ya existe un grupo llamado $user sin la cuenta local esperada; revisar antes de crear."
    printf 'NUEVA cuenta local: %s (UID asignado por el sistema, /home/%s, /bin/bash).\n' "$user" "$user"
  fi
}

preflight_group() {
  local group=$1 local_line global_line
  if local_line=$(local_group_line "$group"); then
    global_line=$(getent group "$group" || true)
    [[ -n "$global_line" ]] || die "El grupo local $group no aparece en NSS; revisa la configuración de grupos."
    printf 'EXISTE grupo local: %s.\n' "$group"
  else
    global_line=$(getent group "$group" || true)
    [[ -z "$global_line" ]] || die "El grupo $group existe en NSS pero no localmente; no crearé una colisión."
    printf 'NUEVO grupo local: %s.\n' "$group"
  fi
}

check_developer_sudo() {
  local user=$1
  if LC_ALL=C sudo -l -U "$user" >/dev/null 2>&1; then
    die "La cuenta $user tiene autorización sudo efectiva según sudo -l. No continuaré ni cambiaré sudoers."
  fi
  printf 'Sin autorización sudo efectiva detectada para %s.\n' "$user"
}

report_adrian_sudo() {
  local identities identity
  identities=$(nss_user_matches adrian || true)
  if [[ -z "$identities" ]]; then
    printf 'Adrián: no se encontró identidad NSS para adrian; sudo queda pendiente de verificar.\n'
    return
  fi
  while IFS= read -r identity; do
    [[ -n "$identity" ]] || continue
    if LC_ALL=C sudo -l -U "$identity" >/dev/null 2>&1; then
      printf 'Adrián (%s): sudo -l detecta autorización efectiva; cuenta de dominio intacta.\n' "$identity"
    else
      printf 'Adrián (%s): sudo -l no confirmó autorización; cuenta de dominio intacta, verificación pendiente.\n' "$identity"
    fi
  done <<< "$identities"
}

command -v getent >/dev/null || die 'No se encontró getent.'
command -v awk >/dev/null || die 'No se encontró awk.'

if [[ "$mode" == apply ]]; then
  [[ $EUID -eq 0 ]] || die 'Para aplicar cambios, ejecuta el script como root (por ejemplo, sudo bash crear-cuentas-equipo.sh --aplicar).'
  for command_name in adduser groupadd usermod gpasswd install passwd sudo sort; do
    command -v "$command_name" >/dev/null || die "No se encontró el comando requerido: $command_name."
  done
  applying=true
fi

printf 'Preflight de cuentas locales del equipo Novagest:\n'
for user in "${ALL_USERS[@]}"; do
  preflight_user "$user"
done

printf '\nPreflight de grupos:\n'
preflight_group "$DEV_GROUP"
preflight_group "$DEPLOY_GROUP"
if ! local_group_line "$SUDO_GROUP" >/dev/null; then
  global_sudo=$(getent group "$SUDO_GROUP" || true)
  [[ -z "$global_sudo" ]] || die "El grupo $SUDO_GROUP existe en NSS pero no localmente."
  die "No existe el grupo local $SUDO_GROUP requerido para ng-marlon."
fi
getent group "$SUDO_GROUP" >/dev/null || die "El grupo local $SUDO_GROUP no aparece en NSS."
printf 'EXISTE grupo local requerido: %s.\n' "$SUDO_GROUP"

if [[ "$mode" == plan ]]; then
  printf '\nPlan listo. No se cambió el sistema. Para aplicar, usa sudo bash %s --aplicar.\n' "$SCRIPT_NAME"
  exit 0
fi

# Valida cuentas anteriores antes de crear nada; no las modifica.
for user in "${DEV_USERS[@]}"; do
  if local_passwd_line "$user" >/dev/null; then
    check_developer_sudo "$user"
  fi
done

printf '\nAplicando cambios. Las cuentas nuevas quedarán sin contraseña y sin llaves autorizadas.\n'
for group in "$DEV_GROUP" "$DEPLOY_GROUP"; do
  if ! local_group_line "$group" >/dev/null; then
    groupadd "$group"
  fi
done

for user in "${ALL_USERS[@]}"; do
  if local_passwd_line "$user" >/dev/null; then
    continue
  fi

  attempting_user=$user
  gecos=$user
  case "$user" in
    ng-ernesto) gecos='Ernesto' ;;
    ng-frank) gecos='Frank' ;;
    ng-brandon) gecos='Brandon' ;;
    ng-marlon) gecos='Marlon' ;;
  esac
  adduser --disabled-password --no-create-home --gecos "$gecos" --shell /bin/bash "$user"
  created_users+=("$user")
  install -d -o "$user" -g "$user" -m 0750 "/home/$user"
  install -d -o "$user" -g "$user" -m 0700 "/home/$user/.ssh"
  install -o "$user" -g "$user" -m 0600 /dev/null "/home/$user/.ssh/authorized_keys"
  status=$(passwd -S "$user")
  read -r _ password_status _ <<< "$status"
  [[ "$password_status" == L ]] || die "La contraseña de $user no quedó bloqueada (passwd -S); no asignaré privilegios."
  [[ -f "/home/$user/.ssh/authorized_keys" && ! -s "/home/$user/.ssh/authorized_keys" ]] || die "authorized_keys de $user no está vacío."
  preflight_user "$user" >/dev/null
  attempting_user=''
  printf 'Cuenta preparada y bloqueada: %s; authorized_keys está vacío.\n' "$user"
done

# Comprueba sudo efectivo antes de agregar cualquier grupo a cuentas nuevas.
for user in "${DEV_USERS[@]}"; do
  if [[ " ${created_users[*]} " == *" $user "* ]]; then
    check_developer_sudo "$user"
  fi
done

add_membership() {
  local user=$1 group=$2
  usermod -aG "$group" "$user"
  added_memberships+=("$user:$group")
  printf 'Membresía asignada: %s -> %s.\n' "$user" "$group"
}

# Solo agrega grupos a cuentas recién creadas; las preexistentes quedan intactas.
for user in "${DEV_USERS[@]}"; do
  if [[ " ${created_users[*]} " == *" $user "* ]]; then
    add_membership "$user" "$DEV_GROUP"
  fi
done
if [[ " ${created_users[*]} " == *' ng-marlon '* ]]; then
  add_membership ng-marlon "$DEPLOY_GROUP"
  add_membership ng-marlon "$SUDO_GROUP"
fi

for user in "${DEV_USERS[@]}"; do
  check_developer_sudo "$user"
done
printf '\nIdentidades locales resultantes:\n'
for user in "${ALL_USERS[@]}"; do
  id "$user"
done
report_adrian_sudo
printf '\nCuentas preparadas. Los usuarios de desarrollo no recibieron sudo.\n'
printf 'Solo ng-marlon creado en esta ejecución se agregó a %s y %s. No se asignaron grupos docker ni permisos de filesystem.\n' "$DEPLOY_GROUP" "$SUDO_GROUP"
printf 'Instala llaves SSH verificadas en un paso posterior; no se copió ninguna llave.\n'
