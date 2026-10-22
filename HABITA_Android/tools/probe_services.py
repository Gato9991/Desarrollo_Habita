"""Comprobación manual de servicios públicos; no participa en el build."""
import concurrent.futures
import json
import sys
import urllib.parse
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

URLS = {
    "weather": "https://api.open-meteo.com/v1/forecast?latitude=-33.5922368&longitude=-70.704706&current=temperature_2m,relative_humidity_2m&timezone=America%2FSantiago",
    "air": "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=-33.5922368&longitude=-70.704706&current=pm2_5,ozone&domains=cams_global&timezone=America%2FSantiago",
    "volcano": "https://visor-grd.senapred.gob.cl/arcgis/rest/services/SIIE_AMENAZAS/amenazas/MapServer/2/query?" + urllib.parse.urlencode({"f":"json", "where":"1=1", "geometry":"-70.704706,-33.5922368", "geometryType":"esriGeometryPoint", "inSR":4326, "spatialRel":"esriSpatialRelIntersects", "outFields":"volcan,peligro", "returnGeometry":"false"}),
    "health": "https://geoportal.cl/geoserver/EstablecimientosdesaluddeChile2026/wfs?" + urllib.parse.urlencode({"service":"WFS", "version":"2.0.0", "request":"GetFeature", "typeNames":"EstablecimientosdesaluddeChile2026:establecimientos_de_salud_junio_2026", "outputFormat":"application/json", "srsName":"EPSG:4326", "bbox":"-70.735,-33.622,-70.674,-33.562,urn:ogc:def:crs:OGC:1.3:CRS84", "count":2000}),
}

def probe(item):
    name, url = item
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "HABITA-Android/0.2 academic prototype"})
        with urllib.request.urlopen(req, timeout=25) as response:
            data = json.load(response)
        summary = {k: data.get(k) for k in ("current", "current_units", "timeStamp", "numberMatched", "numberReturned", "error") if k in data}
        if name == "volcano":
            summary["matches"] = [f.get("attributes") for f in data.get("features", [])]
        return {"service": name, "url": url, "status": "ok" if not data.get("error") else "error", "data": summary}
    except Exception as error:
        return {"service": name, "url": url, "status": "error", "error": str(error)}

if __name__ == "__main__":
    for result in concurrent.futures.ThreadPoolExecutor(max_workers=6).map(probe, URLS.items()):
        print(json.dumps(result, ensure_ascii=False))
