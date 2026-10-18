#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'EOF'
Uso: sudo bash crear-usuario-julio.sh --aplicar --clave-publica /ruta/llave.pub

Crea el usuario personal `julio`, lo agrega a sudo, instala la llave pública
validada e inicia el diálogo interactivo para que Julio elija su contraseña.
No cambia ni elimina otras cuentas.
EOF
}

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die 'Ejecuta como root, por ejemplo con sudo.'
[[ $# -eq 3 && $1 == '--aplicar' && $2 == '--clave-publica' ]] || { usage; exit 2; }
readonly key_file=$3
[[ -t 0 && -t 1 ]] || die 'Se requiere una terminal interactiva para que Julio defina su contraseña.'
[[ -f "$key_file" && ! -L "$key_file" && -r "$key_file" ]] || die 'La llave pública debe ser un archivo regular, legible y no ser un enlace simbólico.'
getent passwd julio >/dev/null && die 'El usuario julio ya existe; no modificaré cuentas existentes.'
getent group sudo >/dev/null || die 'No existe el grupo sudo.'
command -v adduser >/dev/null || die 'No se encontró adduser.'
command -v usermod >/dev/null || die 'No se encontró usermod.'
command -v ssh-keygen >/dev/null || die 'No se encontró ssh-keygen.'
ssh-keygen -l -f "$key_file" >/dev/null || die 'ssh-keygen no pudo validar la llave pública.'

printf 'Llave pública validada:\n'
ssh-keygen -l -f "$key_file"
printf '\nSe creará el usuario julio y se agregará a sudo. Julio definirá su contraseña en el siguiente diálogo.\n'

adduser --disabled-password --gecos 'Julio Ticas' julio
usermod -aG sudo julio
install -d -o julio -g julio -m 0700 /home/julio/.ssh
install -o julio -g julio -m 0600 "$key_file" /home/julio/.ssh/authorized_keys
passwd julio

printf '\nCuenta julio preparada. Desde otra terminal, verifica SSH y sudo antes de usarla para administración.\n'
