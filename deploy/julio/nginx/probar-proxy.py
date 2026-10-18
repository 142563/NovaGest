"""Prueba aislada del proxy con backends simulados; no usa la API ni sudo."""
import http.client
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Backend(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps({"path": self.path, "backend": self.server.server_port,
                           "xff": self.headers.get("X-Forwarded-For")}).encode()
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    do_POST = do_GET

    def log_message(self, *args):
        pass


def available_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def request(port, path, method="GET"):
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=3)
    try:
        conn.request(method, path, headers={"X-Forwarded-For": "203.0.113.99"})
        response = conn.getresponse()
        return response.status, dict(response.getheaders()), response.read()
    finally:
        conn.close()


base = Path(__file__).resolve().parent
servers = []
process = None
try:
    for _ in range(2):
        server = ThreadingHTTPServer(("127.0.0.1", 0), Backend)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        servers.append(server)
    ports = [available_port(), available_port()]
    if ports[0] == ports[1]:
        raise RuntimeError("Puertos temporales repetidos; volver a ejecutar")
    with tempfile.TemporaryDirectory(prefix="novagest-proxy-test-") as tmp:
        root = Path(tmp)
        config = (base / "novagest-privado.conf.template").read_text()
        replacements = {
            "${TAILSCALE_IP}": "127.0.0.1",
            "${NOVAGEST_PROD_PORT}": str(ports[0]),
            "${NOVAGEST_STAGING_PORT}": str(ports[1]),
            "127.0.0.1:8080": f"127.0.0.1:{servers[0].server_port}",
            "127.0.0.1:8081": f"127.0.0.1:{servers[1].server_port}",
            "/etc/nginx/snippets/novagest-proxy-headers.conf": str(base / "novagest-proxy-headers.conf"),
        }
        for old, new in replacements.items():
            config = config.replace(old, new)
        (root / "sites.conf").write_text(config)
        (root / "nginx.conf").write_text(
            f"daemon off; worker_processes 1; pid {root}/nginx.pid;\n"
            f"error_log {root}/error.log; events {{ worker_connections 64; }}\n"
            f"http {{ access_log off; client_body_temp_path {root}/body; "
            f"proxy_temp_path {root}/proxy; include {base}/novagest-rate-limit.conf; "
            f"include {root}/sites.conf; }}\n")
        process = subprocess.Popen(["nginx", "-p", str(root) + "/", "-c", str(root / "nginx.conf")],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        try:
            for attempt in range(40):
                if process.poll() is not None:
                    raise RuntimeError(process.stderr.read().decode())
                try:
                    request(ports[0], "/actuator/health")
                    break
                except OSError:
                    time.sleep(0.1)
            else:
                raise RuntimeError("Nginx temporal no inició")
            for index, port in enumerate(ports):
                for path in ("/actuator/health", "/api/tramites?pagina=1"):
                    status, headers, body = request(port, path)
                    data = json.loads(body)
                    assert status == 200 and data["path"] == path, (status, body)
                    assert data["backend"] == servers[index].server_port
                    assert data["xff"] == "127.0.0.1", data
                    assert headers["X-Content-Type-Options"] == "nosniff"
                    assert headers["X-Frame-Options"] == "DENY"
            statuses = [request(ports[0], "/api/auth/login", "POST")[0] for _ in range(15)]
            assert 200 in statuses and 429 in statuses, statuses
            print("OK: entornos separados; /api y query preservados; health; cabeceras; XFF saneado; login 429")
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            process.stderr.close()
            process = None
finally:
    if process is not None and process.poll() is None:
        process.kill()
        process.wait()
    for server in servers:
        server.shutdown()
        server.server_close()
