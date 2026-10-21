#!/usr/bin/env python3
"""Ejecutar: python Pruebas/probar_habita.py [--no-abrir].

Lee las fuentes de este repositorio; no usa API, red ni valores de demostración.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import html
import json
import subprocess
import sys
import webbrowser
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from fuentes import Fuentes
from modelo import (
    FIN, INICIO, VERSION, PESOS, MIN_HORAS, MIN_FRACCION_DIAS, MIN_MESES,
    RADIO_ESTACION_M, MIN_COBERTURA_GLOBAL, accesibilidad, combinar,
    distancia, elegir_estacion, global_habita,
)

PALACIO_LA_MONEDA = (-33.4422475, -70.653898)
COSTANERA_CENTER = (-33.4173748, -70.6072617)
ESTADIO_NACIONAL = (-33.4644994, -70.6112756)
AEROPUERTO_SANTIAGO = (-33.3972119, -70.7930097)
CERRO_SAN_CRISTOBAL_PIO_NONO = (-33.4304869, -70.6353589)
HOSPITAL_SOTERO_DEL_RIO = (-33.5770095, -70.5812289)
INTERMODAL_LA_CISTERNA = (-33.5382168, -70.6641985)
OBSERVATORIO_CERRO_CALAN = (-33.3964895, -70.5370203)

UBICACIONES = {
    "Palacio de La Moneda": PALACIO_LA_MONEDA,
    "Costanera Center (Cenco Costanera)": COSTANERA_CENTER,
    "Estadio Nacional Julio Martínez Prádanos": ESTADIO_NACIONAL,
    "Aeropuerto Internacional Arturo Merino Benítez": AEROPUERTO_SANTIAGO,
    "Cerro San Cristóbal — acceso Pío Nono": CERRO_SAN_CRISTOBAL_PIO_NONO,
    "Hospital Dr. Sótero del Río": HOSPITAL_SOTERO_DEL_RIO,
    "Estación Intermodal La Cisterna": INTERMODAL_LA_CISTERNA,
    "Observatorio Astronómico Nacional — Cerro Calán": OBSERVATORIO_CERRO_CALAN,
}
NOMBRES = {"aire": "Calidad del aire", "paraderos": "Paraderos",
           "salud": "Salud", "bienestar": "Bienestar térmico"}
COMMIT_FUENTES_REFERENCIA = "ee17d1d6b471619d2f50a214fe588a1cb3b267f7"


def parametro_estacion(nombre, lat, lon, estaciones):
    s = elegir_estacion(lat, lon, estaciones)
    componentes = {c: s["resumenes"][c]["puntaje_100"] if s else None for c in PESOS[nombre]}
    resultado = combinar(nombre, componentes)
    resultado["estacion"] = s
    resultado["estado"] = ("Disponible" if resultado["cobertura"] >= 1 - 1e-12 else "Parcial") if s else "Sin estación admisible"
    resultado["observaciones"] = []
    if s:
        for c, r in s["resumenes"].items():
            if r.get("estado") != "Completo":
                resultado["observaciones"].append(c + ": " + r["estado"])
        meses = [tuple(r.get("meses_usados", [])) for r in s["resumenes"].values()
                 if r["puntaje_100"] is not None]
        if len(set(meses)) > 1:
            resultado["observaciones"].append("Los componentes usan meses distintos.")
    else:
        resultado["observaciones"].append("Ninguna estación admisible a 30 km o menos.")
        if estaciones:
            cercana = min(estaciones, key=lambda e: (
                distancia(lat, lon, e["latitud"], e["longitud"]), int(e["codigo"])))
            resultado["referencia_no_asignada"] = {
                "codigo": cercana["codigo"], "nombre": cercana["nombre"],
                "distancia_m": distancia(lat, lon, cercana["latitud"], cercana["longitud"]),
                "resumenes": cercana["resumenes"],
                "nota": "Solo referencia geográfica: NO se usó para calcular.",
            }
    return resultado


def calcular(raiz):
    fuentes = Fuentes(raiz)
    carpeta = Path(__file__).resolve().parent
    config_dmc = fuentes.json(carpeta / "estaciones_dmc.json")
    config_aire = fuentes.json(carpeta / "estaciones_aire.json")
    print("Leyendo Excel de temperatura y humedad...", flush=True)
    clima = fuentes.clima(config_dmc)
    print("Revisando CSV de aire y metadatos...", flush=True)
    aire = fuentes.aire(config_aire)
    print("Leyendo catálogo MINSAL de junio de 2026...", flush=True)
    salud = fuentes.salud()
    print("Verificando y leyendo las nueve partes del GTFS...", flush=True)
    paraderos = fuentes.paraderos()
    resultados = []
    for nombre, (lat, lon) in UBICACIONES.items():
        parametros = {
            "aire": parametro_estacion("aire", lat, lon, aire),
            "paraderos": accesibilidad("paraderos", lat, lon, paraderos, 500),
            "salud": accesibilidad("salud", lat, lon, salud, 2000),
            "bienestar": parametro_estacion("bienestar", lat, lon, clima),
        }
        resultados.append({
            "ubicacion": nombre, "latitud": lat, "longitud": lon,
            "indice": global_habita(parametros), "parametros": parametros,
        })
    for archivo in sorted(carpeta.glob("*.py")):
        fuentes.registrar(archivo)
    fuentes.registrar(carpeta / "plantilla.html")
    commit_local = None
    try:
        proc = subprocess.run(["git", "-C", str(raiz), "rev-parse", "--show-toplevel", "HEAD"],
                              capture_output=True, text=True, timeout=5, check=False)
        if proc.returncode == 0:
            lineas = proc.stdout.strip().splitlines()
            if len(lineas) == 2 and Path(lineas[0]).resolve() == Path(raiz).resolve():
                commit_local = lineas[1]
    except (OSError, subprocess.TimeoutExpired):
        pass
    return {
        "modelo": VERSION, "estado_modelo": "Prueba pendiente de aprobación",
        "generado_utc": datetime.now(timezone.utc).isoformat(),
        "periodo": {"inicio": INICIO.isoformat(), "fin": FIN.isoformat(),
                    "octubre": "Parcial: 13 días; no se extrapola"},
        "reglas": {"pesos_globales": {k: .25 for k in PESOS}, "pesos_internos": PESOS,
                   "min_horas_dia": MIN_HORAS, "min_fraccion_dias_mes": MIN_FRACCION_DIAS,
                   "min_meses": MIN_MESES, "radio_estacion_m": RADIO_ESTACION_M,
                   "min_cobertura_global": MIN_COBERTURA_GLOBAL},
        "commit_fuentes_referencia": COMMIT_FUENTES_REFERENCIA,
        "commit_local": commit_local,
        "nota_trazabilidad": "Los SHA256 listados identifican los bytes realmente leídos; el commit de referencia no certifica una copia local modificada.",
        "catalogos": fuentes.catalogos, "avisos": fuentes.avisos,
        "auditoria_estaciones": {"dmc": clima, "aire": aire},
        "archivos_leidos": list(fuentes.archivos.values()), "resultados": resultados,
    }


def fmt(x, dec=1):
    return "Sin dato" if x is None else f"{x:.{dec}f}".replace(".", ",")


def tabla_consola(datos):
    encabezado = ["Ubicación", "Aire /10", "Par. /10", "Salud /10", "Térm. /10", "HABITA /10", "Cobertura"]
    lineas = []
    for i, r in enumerate(datos["resultados"], 1):
        p = r["parametros"]
        lineas.append([f'{i}. {r["ubicacion"]}'] +
                      [fmt(p[k]["puntaje_100"]/10 if p[k]["puntaje_100"] is not None else None)
                       for k in NOMBRES] +
                      [fmt(r["indice"]["puntaje_10"]), fmt(100*r["indice"]["cobertura"], 0)+" %"])
    anchos = [max(len(x[j]) for x in [encabezado]+lineas) for j in range(len(encabezado))]
    borde = "+" + "+".join("-"*(a+2) for a in anchos) + "+"
    def linea(celdas):
        return "| " + " | ".join(str(c).ljust(a) for c, a in zip(celdas, anchos)) + " |"
    print("\nHABITA — PRUEBA 0.2.0 | 01-07-2026 a 13-10-2026\n")
    print(borde)
    print(linea(encabezado))
    print(borde)
    for fila in lineas:
        print(linea(fila))
        print(borde)
    print("Todos los puntajes en escala 0–10. Cobertura = componentes disponibles ponderados.")
    print("Un global parcial redistribuye el peso de componentes disponibles; no equivale a un índice completo.")
    print("No es un ranking validado ni una versión oficial.")


def e(x):
    return html.escape(str(x), quote=True)


def tabla_html(headers, rows):
    return "<div class='scroll'><table><thead><tr>" + "".join(
        "<th>"+e(h)+"</th>" for h in headers) + "</tr></thead><tbody>" + "".join(
        "<tr>"+"".join("<td>"+e(c)+"</td>" for c in fila)+"</tr>" for fila in rows
    ) + "</tbody></table></div>"


def resumen_serie_html(nombre, r):
    meses = r.get("meses", [])
    texto = ("<p><b>"+e(nombre)+"</b> · "+e(r["estado"])+" · puntaje "+
             fmt(r["puntaje_100"]/10 if r["puntaje_100"] is not None else None)+
             "/10 · valor medio "+fmt(r.get("valor_medio"))+"</p>")
    if meses:
        texto += tabla_html(["Mes", "Días válidos / esperados", "Admisible", "Puntaje /10", "Valor medio"],
                            [[m["mes"], f'{m["dias_admisibles"]} / {m["dias_esperados"]}',
                              "Sí" if m["admisible"] else "No",
                              fmt(m["puntaje_100"]/10 if m["puntaje_100"] is not None else None),
                              fmt(m["valor_medio"])] for m in meses])
    archivos = r.get("archivos", [r["archivo"]] if "archivo" in r else [])
    texto += "<ul class='paths'>"+"".join("<li>"+e(a)+"</li>" for a in archivos)+"</ul>"
    if r.get("conteos_validacion_en_periodo") is not None:
        texto += "<p>Columnas de validación (filas en período): "+e(r["conteos_validacion_en_periodo"])+"</p>"
    return texto


def escribir_html(datos, destino):
    filas = []
    detalles = []
    for i, r in enumerate(datos["resultados"], 1):
        p, ind = r["parametros"], r["indice"]
        celdas = [f'{i:02d}', r["ubicacion"]]
        for k in NOMBRES:
            v = p[k]["puntaje_100"]
            celdas.append(fmt(v/10 if v is not None else None))
        celdas += [fmt(ind["puntaje_10"]), fmt(100*ind["cobertura"], 0)+" %", ind["estado"]]
        filas.append(celdas)
        texto = f'<details><summary><span>{i:02d}</span> {e(r["ubicacion"])} <small>Ver cálculo y fuentes</small></summary><div class="detail">'
        texto += f'<p>Latitud {r["latitud"]} · Longitud {r["longitud"]}</p>'
        texto += tabla_html(["Parámetro", "Cobertura interna", "Participación final en el global"],
            [[NOMBRES[k], fmt(100*p[k]["cobertura"], 0)+" %",
              fmt(100*ind["participacion_final"][k], 2)+" %"] for k in NOMBRES])
        for k in ("paraderos", "salud"):
            a = p[k]
            cercano = a["cercano"]
            texto += "<h3>"+e(NOMBRES[k])+"</h3>"
            texto += ("<p>Distancia: "+fmt(a["distancia_m"])+" m · Cantidad en "+
            str(a.get("radio_m", 500 if k == "paraderos" else 2000))+" m: "+e(a["cantidad"])+"</p>")
            if cercano:
                texto += "<p>Más cercano: "+e(cercano["nombre"])+" · Código "+e(cercano["codigo"])+"</p>"
        for k in ("bienestar", "aire"):
            a = p[k]
            texto += "<h3>"+e(NOMBRES[k])+"</h3>"
            estacion = a["estacion"]
            if estacion:
                texto += ("<p>"+e(estacion["nombre"])+" · "+e(estacion["codigo"])+" · "+
                fmt(estacion["distancia_m"]/1000, 2)+" km · <a href='"+e(estacion["fuente"])+"'>Ficha oficial</a></p>")
                for c, resumen in estacion["resumenes"].items():
                    texto += resumen_serie_html(c, resumen)
            else:
                texto += "<p>Sin estación admisible. No participa en el cálculo.</p>"
                ref = a.get("referencia_no_asignada")
                if ref:
                    texto += ("<p>Referencia geográfica sin asignar: "+e(ref["nombre"])+" · "+
                    fmt(ref["distancia_m"]/1000, 2)+" km. "+e(ref["nota"])+"</p>")
                    for c, resumen in ref["resumenes"].items():
                        texto += resumen_serie_html(c, resumen)
            texto += "<ul>"+"".join("<li>"+e(x)+"</li>" for x in a["observaciones"])+"</ul>"
        texto += "</div></details>"
        detalles.append(texto)
    tabla = tabla_html(["N.º", "Ubicación", "Aire /10", "Paraderos /10", "Salud /10",
                        "Térmico /10", "HABITA /10", "Cobertura", "Estado"], filas)
    rutas = tabla_html(["Archivo leído", "Bytes", "SHA256"],
                       [[x["ruta"], x["bytes"], x["sha256"]] for x in datos["archivos_leidos"]])
    plantilla = Path(__file__).with_name("plantilla.html").read_text("utf-8")
    salida = plantilla.replace("{{TABLA}}", tabla).replace("{{DETALLES}}", "".join(detalles))
    salida = salida.replace("{{RUTAS}}", rutas).replace("{{FECHA}}", e(datos["generado_utc"]))
    coberturas = [100*r["indice"]["cobertura"] for r in datos["resultados"]]
    etiqueta = fmt(min(coberturas), 0)+" %" if min(coberturas) == max(coberturas) else fmt(min(coberturas), 0)+"–"+fmt(max(coberturas), 0)+" %"
    salida = salida.replace("{{COBERTURA}}", etiqueta)
    faltan_aire = any(r["parametros"]["aire"]["puntaje_100"] is None for r in datos["resultados"])
    nota = "Aire sin puntaje mientras falten metadatos de su exportación. Sin dato no significa mala calidad del aire. El global mostrado es parcial." if faltan_aire else "Revisa cobertura y meses por componente antes de comparar ubicaciones. Los datos preliminares de aire conservan su condición."
    salida = salida.replace("{{NOTAS}}", nota)
    salida = salida.replace("{{AVISOS}}", "<ul>"+"".join("<li>"+e(a)+"</li>" for a in datos["avisos"])+"</ul>" if datos["avisos"] else "<p>Sin errores de lectura detectados.</p>")
    destino.write_text(salida, encoding="utf-8")


def guardar(datos, carpeta):
    carpeta.mkdir(parents=True, exist_ok=True)
    (carpeta/"detalle_habita.json").write_text(
        json.dumps(datos, ensure_ascii=False, indent=2, allow_nan=False)+"\n", "utf-8")
    with (carpeta/"resultados_habita.csv").open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["ubicacion", "latitud", "longitud", "aire_0_10", "paraderos_0_10",
                    "salud_0_10", "bienestar_0_10", "habita_0_10", "cobertura_pct", "estado"])
        for r in datos["resultados"]:
            w.writerow([r["ubicacion"], fmt(r["latitud"],7), fmt(r["longitud"],7)] +
                       [fmt(r["parametros"][k]["puntaje_100"]/10
                            if r["parametros"][k]["puntaje_100"] is not None else None, 6)
                        for k in NOMBRES] +
                       [fmt(r["indice"]["puntaje_10"], 6), fmt(100*r["indice"]["cobertura"], 2),
                        r["indice"]["estado"]])
    escribir_html(datos, carpeta/"resultados_habita.html")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--raiz", type=Path, default=Path(__file__).resolve().parents[1],
                        help="Raíz de Desarrollo_Habita; por defecto se deduce desde este script.")
    parser.add_argument("--salida", type=Path, default=Path(__file__).resolve().parent/"resultados")
    parser.add_argument("--no-abrir", action="store_true", help="No abrir el informe en el navegador.")
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    try:
        datos = calcular(args.raiz.resolve())
        guardar(datos, args.salida.resolve())
    except (OSError, ValueError, KeyError, ImportError, StopIteration, zipfile.BadZipFile, csv.Error) as exc:
        parser.exit(1, "No se pudo calcular la prueba: "+str(exc)+
                    "\nComprueba la copia completa del repositorio y pip install -r Pruebas/requirements.txt\n")
    tabla_consola(datos)
    informe = (args.salida/"resultados_habita.html").resolve()
    print("\nInforme:", informe)
    if not args.no_abrir:
        webbrowser.open(informe.as_uri())


if __name__ == "__main__":
    main()
