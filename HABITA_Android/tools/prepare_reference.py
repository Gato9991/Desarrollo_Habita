"""Regenera el catálogo Android usando los lectores auditados de Pruebas.

Ejecutar desde cualquier carpeta con Python y openpyxl. Android Studio no
necesita Python: el JSON resultante se conserva como asset versionado.
"""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, str(ROOT / "Pruebas"))
from fuentes import Fuentes
from modelo import VERSION, INICIO, FIN, INICIO_AIRE, FIN_AIRE
from probar_habita import UBICACIONES, parametro_estacion, accesibilidad, global_habita

sources = Fuentes(ROOT)
climate = sources.clima(sources.json(ROOT / "Pruebas/estaciones_dmc.json"))
air = sources.aire(sources.json(ROOT / "Pruebas/estaciones_aire.json"))
health = sources.salud()
stops = sources.paraderos()
for name in ("fuentes.py", "modelo.py", "probar_habita.py"):
    sources.registrar(ROOT / "Pruebas" / name)
snapshot = {
    "schema": 1, "model": VERSION,
    "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
    "air_period": [str(INICIO_AIRE), str(FIN_AIRE)],
    "thermal_period": [str(INICIO), str(FIN)],
    "air": air, "thermal": climate, "health": health, "stops": stops,
    "catalogs": sources.catalogos, "warnings": sources.avisos,
    "sources": sorted(sources.archivos.values(), key=lambda x: x["ruta"]),
}
output = ROOT / "HABITA_Android/app/src/main/assets/reference/catalog.json"
output.parent.mkdir(parents=True, exist_ok=True)
encoded = (json.dumps(snapshot, ensure_ascii=False, separators=(",", ":"), allow_nan=False) + "\n").encode()
output.write_bytes(encoded)
output.with_suffix(".sha256").write_text(hashlib.sha256(encoded).hexdigest() + "\n", encoding="ascii")
expected = []
for name, (lat, lon) in UBICACIONES.items():
    parameters = {
        "aire": parametro_estacion("aire", lat, lon, air),
        "paraderos": accesibilidad("paraderos", lat, lon, stops, 500),
        "salud": accesibilidad("salud", lat, lon, health, 2000),
        "bienestar": parametro_estacion("bienestar", lat, lon, climate),
    }
    expected.append({"name": name, "lat": lat, "lon": lon,
                     "parameters": parameters, "index": global_habita(parameters)})
test_output = ROOT / "HABITA_Android/core/src/test/resources/territory_reference.json"
test_output.write_text(json.dumps(expected, ensure_ascii=False, allow_nan=False), encoding="utf-8")
print(json.dumps({"bytes": len(encoded), "air_stations": len(air), "thermal_stations": len(climate),
                  "health": len(health), "stops": len(stops), "warnings": sources.avisos}, ensure_ascii=False))
