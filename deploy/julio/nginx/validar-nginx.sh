#!/usr/bin/env bash
set -euo pipefail

# Valida la plantilla privada con puertos e IP loopback temporales. No necesita
# sudo, no toca la configuracion activa y elimina sus archivos temporales al salir.
base_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
nginx_bin="${NGINX_BIN:-nginx}"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/novagest-nginx-check.XXXXXX")"
trap 'rm -rf -- "$tmp_dir"' EXIT

mkdir -p "$tmp_dir/conf" "$tmp_dir/snippets"
cp "$base_dir/novagest-proxy-headers.conf" "$tmp_dir/snippets/novagest-proxy-headers.conf"

# Renderizar solo variables de la plantilla para no alterar variables propias de nginx.
TAILSCALE_IP=127.0.0.1 \
NOVAGEST_PROD_PORT=18082 \
NOVAGEST_STAGING_PORT=18083 \
    envsubst '${TAILSCALE_IP} ${NOVAGEST_PROD_PORT} ${NOVAGEST_STAGING_PORT}' \
    < "$base_dir/novagest-privado.conf.template" \
    > "$tmp_dir/conf/novagest-privado.conf"

cat > "$tmp_dir/nginx.conf" <<EOF
worker_processes 1;
pid $tmp_dir/nginx.pid;
error_log $tmp_dir/error.log notice;
events { worker_connections 64; }
http {
    access_log off;
    include "$base_dir/novagest-rate-limit.conf";
    include $tmp_dir/conf/novagest-privado.conf;
}
EOF

# Ajustar los includes absolutos de snippets para que apunten al arbol temporal.
sed -i "s|/etc/nginx/snippets/novagest-proxy-headers.conf|$tmp_dir/snippets/novagest-proxy-headers.conf|g" \
    "$tmp_dir/conf/novagest-privado.conf"

"$nginx_bin" -t -p "$tmp_dir/" -c "$tmp_dir/nginx.conf"
