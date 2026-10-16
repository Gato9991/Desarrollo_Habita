#!/usr/bin/env python3
"""HABITA: GUI local conectada al motor GTFS 1.2.1."""
from __future__ import annotations
import argparse
import csv
from datetime import date
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import math
import mimetypes
from pathlib import Path
import re
import secrets
import threading
import time
from urllib.parse import urlsplit, unquote
import uuid
import webbrowser
import zipfile
from gtfs_analyzer_v1_2_1 import GTFSAnalyzer, dumps_strict, to_native

ROOT = Path(__file__).resolve().parent
WEB = ROOT / "web"
DEFAULT_FEED = ROOT / "data" / "GTFS_20260829.zip"
EXAMPLES = [
    {"name": "Plaza de Armas · San Bernardo", "lat": -33.5922368, "lon": -70.704706},
    {"name": "Estadio Nacional", "lat": -33.4644994, "lon": -70.6112756},
]
MAX_UPLOAD = 300 * 1024 * 1024


def ensure_bundled_feed():
    """Reconstruye una sola vez el ZIP original distribuido en partes verificadas."""
    if DEFAULT_FEED.is_file():
        return
    import hashlib
    import os
    manifest = json.loads((ROOT / "data" / "manifest.json").read_text(encoding="utf-8"))
    temporary = DEFAULT_FEED.with_suffix(".building")
    digest = hashlib.sha256()
    size = 0
    try:
        with temporary.open("wb") as out:
            for name in manifest["parts"]:
                # Los nombres vienen del manifiesto incluido con la aplicación.
                part = ROOT / "data" / Path(name).name
                with part.open("rb") as source:
                    while block := source.read(1024 * 1024):
                        out.write(block)
                        digest.update(block)
                        size += len(block)
        if size != manifest["size"] or digest.hexdigest() != manifest["sha256"]:
            raise ValueError("El feed incluido está incompleto. Descarga de nuevo toda la carpeta.")
        os.replace(temporary, DEFAULT_FEED)
    finally:
        temporary.unlink(missing_ok=True)


def validate_query(body):
    try:
        lat, lon = float(body["lat"]), float(body["lon"])
    except (KeyError, TypeError, ValueError):
        raise ValueError("Ingresa latitud y longitud numéricas con punto decimal.")
    if not math.isfinite(lat) or not -90 <= lat <= 90:
        raise ValueError("La latitud debe estar entre -90 y 90.")
    if not math.isfinite(lon) or not -180 <= lon <= 180:
        raise ValueError("La longitud debe estar entre -180 y 180.")
    selected = str(body.get("date", ""))
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", selected):
        raise ValueError("La fecha debe tener formato AAAA-MM-DD.")
    date.fromisoformat(selected)
    name = str(body.get("name", "")).strip()[:160] or "Ubicación consultada"
    return lat, lon, selected, name

def read_feed_metadata(path):
    p = Path(path)
    if p.is_dir():
        with (p / "feed_info.txt").open(encoding="utf-8-sig", newline="") as f:
            row = next(csv.DictReader(f))
    else:
        with zipfile.ZipFile(p) as z:
            names = [n for n in z.namelist() if n.rsplit("/", 1)[-1] == "feed_info.txt"]
            if not names:
                raise ValueError("El ZIP no contiene feed_info.txt.")
            with io.TextIOWrapper(z.open(names[0]), encoding="utf-8-sig", newline="") as f:
                row = next(csv.DictReader(f))
    def iso(value):
        return date(int(value[:4]), int(value[4:6]), int(value[6:8])).isoformat()
    start, end = iso(row["feed_start_date"]), iso(row["feed_end_date"])
    today = date.today().isoformat()
    return {"version": row.get("feed_version", "Sin versión"), "start": start, "end": end,
            "suggested_date": min(max(today, start), end), "label": p.name}

class AppState:
    def __init__(self, feed=DEFAULT_FEED):
        self.token = secrets.token_urlsafe(32)
        if Path(feed).resolve() == DEFAULT_FEED.resolve():
            ensure_bundled_feed()
        self.feed = Path(feed).expanduser().resolve()
        self.metadata = read_feed_metadata(self.feed)
        self.analyzer = None
        self.lock = threading.Lock()
        self.jobs = {}
        self.busy = False

    def config(self):
        return {"token": self.token, "feed": self.metadata, "examples": EXAMPLES,
                "version": "1.2.1 GUI", "busy": self.busy}

    def start_analysis(self, body):
        query = validate_query(body)
        if not self.lock.acquire(blocking=False):
            raise RuntimeError("Ya hay un análisis en curso. Espera a que termine.")
        job_id = uuid.uuid4().hex
        self.jobs[job_id] = {"status": "running", "phase": "Cargando el feed GTFS…"}
        self.busy = True
        for old in list(self.jobs)[:-8]:
            self.jobs.pop(old, None)
        threading.Thread(target=self._analyze, args=(job_id, query), daemon=True).start()
        return job_id

    def _analyze(self, job_id, query):
        job = self.jobs[job_id]
        try:
            if self.analyzer is None:
                self.analyzer = GTFSAnalyzer(str(self.feed))
            job["phase"] = "Calculando paraderos, recorridos y horarios…"
            report = to_native(self.analyzer.analyze(*query))
            job.update(status="done", phase="Análisis completado",
                       result=json.loads(dumps_strict(report)))
        except Exception as exc:
            job.update(status="error", error=str(exc))
        finally:
            self.busy = False
            self.lock.release()

    def switch_feed(self, path):
        if not self.lock.acquire(blocking=False):
            raise RuntimeError("Espera a que termine el análisis antes de cambiar el feed.")
        try:
            candidate = Path(path).expanduser().resolve()
            metadata = read_feed_metadata(candidate)
            analyzer = GTFSAnalyzer(str(candidate))
            self.feed, self.metadata, self.analyzer = candidate, metadata, analyzer
            return metadata
        finally:
            self.lock.release()

class LocalServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True
    def __init__(self, address, state):
        super().__init__(address, Handler)
        self.state = state

class Handler(BaseHTTPRequestHandler):
    server_version = "HABITA/1.2.1"
    def log_message(self, fmt, *args):
        pass

    def _host_ok(self):
        port = self.server.server_address[1]
        return self.headers.get("Host", "") in {f"127.0.0.1:{port}", f"localhost:{port}"}

    def _send(self, status, payload, mime="application/json; charset=utf-8", download=None):
        data = payload if isinstance(payload, bytes) else dumps_strict(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'")
        if download:
            self.send_header("Content-Disposition", f'attachment; filename="{download}"')
        self.end_headers()
        try:
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        if not self._host_ok():
            return self._send(403, {"error": "Host no permitido."})
        url = urlsplit(self.path)
        if url.path == "/api/config":
            return self._send(200, self.server.state.config())
        if url.path.startswith("/api/jobs/"):
            parts = url.path.strip("/").split("/")
            job = self.server.state.jobs.get(parts[2]) if len(parts) >= 3 else None
            if job is None:
                return self._send(404, {"error": "Informe no disponible. Ejecuta el análisis otra vez."})
            if len(parts) == 4 and parts[3] == "download":
                if job["status"] != "done":
                    return self._send(409, {"error": "El informe todavía no está listo."})
                report = job["result"]
                filename = "HABITA_movilidad_" + report["metadata"]["analysis_date"] + ".json"
                return self._send(200, report, download=filename)
            return self._send(200, job)
        relative = "index.html" if url.path == "/" else url.path.lstrip("/")
        candidate = (WEB / relative).resolve()
        if not candidate.is_relative_to(WEB.resolve()) or not candidate.is_file():
            return self._send(404, {"error": "No encontrado."})
        mime = mimetypes.guess_type(str(candidate))[0] or "application/octet-stream"
        if mime.startswith("text/") or mime in {"application/javascript", "application/json"}:
            mime += "; charset=utf-8"
        self._send(200, candidate.read_bytes(), mime)

    def do_POST(self):
        if not self._host_ok():
            return self._send(403, {"error": "Host no permitido."})
        port = self.server.server_address[1]
        origin = self.headers.get("Origin")
        if origin and origin not in {f"http://127.0.0.1:{port}", f"http://localhost:{port}"}:
            return self._send(403, {"error": "Origen no permitido."})
        if not secrets.compare_digest(self.headers.get("X-Habita-Token", ""), self.server.state.token):
            return self._send(403, {"error": "Sesión inválida. Recarga la página."})
        temp_path = None
        try:
            length = int(self.headers.get("Content-Length", "0"))
            path = urlsplit(self.path).path
            if path == "/api/feed/upload":
                if not 0 < length <= MAX_UPLOAD:
                    return self._send(413, {"error": "Selecciona un ZIP de hasta 300 MB."})
                folder = ROOT / "uploads"
                folder.mkdir(exist_ok=True)
                temp_path = folder / (uuid.uuid4().hex + ".zip")
                remaining = length
                with temp_path.open("wb") as f:
                    while remaining:
                        block = self.rfile.read(min(1024 * 1024, remaining))
                        if not block:
                            raise ValueError("La carga se interrumpió. Vuelve a seleccionar el ZIP.")
                        f.write(block)
                        remaining -= len(block)
                metadata = self.server.state.switch_feed(temp_path)
                metadata["label"] = Path(unquote(self.headers.get("X-File-Name", "GTFS cargado.zip"))).name[:150]
                temp_path = None
                return self._send(200, {"feed": metadata})
            if not 0 < length <= 16384:
                return self._send(413, {"error": "Solicitud vacía o demasiado grande."})
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                raise ValueError("La solicitud debe ser un objeto JSON.")
            if path == "/api/analyze":
                return self._send(202, {"job_id": self.server.state.start_analysis(body)})
            if path == "/api/feed/path":
                return self._send(200, {"feed": self.server.state.switch_feed(str(body.get("path", "")))})
            return self._send(404, {"error": "Acción no encontrada."})
        except RuntimeError as exc:
            self._send(409, {"error": str(exc)})
        except Exception as exc:
            self._send(400, {"error": str(exc)})
        finally:
            if temp_path is not None:
                temp_path.unlink(missing_ok=True)

def main():
    parser = argparse.ArgumentParser(description="HABITA · Movilidad GTFS con interfaz gráfica local")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--gtfs", default=str(DEFAULT_FEED))
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()
    try:
        state = AppState(args.gtfs)
    except Exception as exc:
        print(f"No se pudo abrir el feed GTFS: {exc}")
        return 1
    server = None
    for port in range(args.port, min(args.port + 20, 65536)):
        try:
            server = LocalServer(("127.0.0.1", port), state)
            break
        except OSError:
            continue
    if server is None:
        print("No hay un puerto local disponible. Prueba --port 9000.")
        return 1
    url = f"http://127.0.0.1:{server.server_address[1]}"
    print(f"HABITA GUI lista en {url}\nMantén esta ventana abierta. Ctrl+C para cerrar.", flush=True)
    if not args.no_browser:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
