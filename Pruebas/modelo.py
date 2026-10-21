"""Fórmulas puras de HABITA 0.2.0; propuesta en evaluación, no versión oficial."""
from __future__ import annotations

from collections import defaultdict
from datetime import date, datetime, timedelta
from math import asin, cos, isfinite, radians, sin, sqrt
from statistics import fmean

VERSION = "0.2.0-prueba.1"
INICIO = date(2026, 7, 1)
FIN = date(2026, 10, 13)
PESOS = {
    "aire": {"mp25": .70, "o3": .30},
    "paraderos": {"distancia": .60, "cantidad": .40},
    "salud": {"distancia": .70, "cantidad": .30},
    "bienestar": {"temperatura": .60, "humedad": .40},
}
MIN_HORAS = 18
MIN_FRACCION_DIAS = .70
MIN_MESES = 2
RADIO_ESTACION_M = 30_000
MIN_COBERTURA_GLOBAL = .75


def numero(x):
    if x is None or isinstance(x, bool):
        return None
    try:
        v = float(str(x).strip().replace(",", "."))
    except (ValueError, TypeError):
        return None
    return v if isfinite(v) else None


def limitar(x):
    return max(0.0, min(100.0, x))


def puntuar(componente, valor):
    x = numero(valor)
    if x is None:
        return None
    if componente == "temperatura":
        return limitar(100 * (1 - abs(x - 22) / 18))
    if componente == "humedad":
        return limitar(100 * (1 - abs(x - 50) / 50)) if 0 <= x <= 100 else None
    if x < 0:
        return None
    if componente == "mp25":
        return limitar(100 * (1 - x / 100))
    if componente == "o3":
        return limitar(100 * (1 - x / 200))
    if componente == "distancia_paradero":
        return limitar(100 * (1 - x / 1000))
    if componente == "distancia_salud":
        return limitar(100 * (1 - x / 5000))
    if componente in ("cantidad_paraderos", "cantidad_salud"):
        if not x.is_integer():
            return None
        return limitar(x * (10 if componente == "cantidad_paraderos" else 20))
    raise ValueError("Componente desconocido: " + componente)


def distancia(lat1, lon1, lat2, lon2):
    a, b, c, d = map(radians, (lat1, lon1, lat2, lon2))
    h = sin((c-a)/2)**2 + cos(a)*cos(c)*sin((d-b)/2)**2
    return 2 * 6_371_000 * asin(sqrt(max(0, min(1, h))))


def coordenadas_validas(lat, lon):
    a, b = numero(lat), numero(lon)
    return a is not None and b is not None and -90 <= a <= 90 and -180 <= b <= 180


def combinar(nombre, componentes):
    pesos = PESOS[nombre]
    usados = {k: v for k, v in componentes.items() if k in pesos and numero(v) is not None}
    c = sum(pesos[k] for k in usados)
    score = sum(pesos[k] * v for k, v in usados.items()) / c if c else None
    return {"puntaje_100": score, "cobertura": c, "componentes": componentes}


def global_habita(parametros):
    # Los términos ausentes se omiten; jamás se hace 0 * None.
    cobertura = sum(.25 * p["cobertura"] for p in parametros.values())
    bruto = (sum(.25 * p["cobertura"] * p["puntaje_100"]
                 for p in parametros.values() if p["puntaje_100"] is not None) / cobertura
             if cobertura else None)
    publicable = cobertura >= MIN_COBERTURA_GLOBAL - 1e-12
    return {
        "puntaje_100": bruto if publicable else None,
        "puntaje_10": bruto / 10 if publicable else None,
        "cobertura": cobertura,
        "estado": ("Completo" if cobertura >= 1 - 1e-12 else "Parcial") if publicable
                  else ("Sin datos" if not cobertura else "Cobertura insuficiente"),
        "participacion_final": {
            k: .25 * p["cobertura"] / cobertura if cobertura else 0
            for k, p in parametros.items()
        },
    }


def meses_esperados(inicio=INICIO, fin=FIN):
    meses = defaultdict(int)
    dia = inicio
    while dia <= fin:
        meses[dia.strftime("%Y-%m")] += 1
        dia += timedelta(days=1)
    return dict(meses)


def resumir_serie(observaciones, componente, agregacion="horaria",
                   inicio=INICIO, fin=FIN):
    """Puntúa observaciones; horas -> días -> meses con igual peso mensual.

    Las filas repetidas idénticas se deduplican; valores distintos para la misma
    marca se excluyen como conflicto. No se modifica el archivo fuente.
    """
    por_marca = defaultdict(set)
    rechazados = 0
    fuera_periodo = 0
    for marca, valor in observaciones:
        if not inicio <= marca.date() <= fin:
            fuera_periodo += 1
            continue
        x = numero(valor)
        if x is None or puntuar(componente, x) is None:
            rechazados += 1
            continue
        por_marca[marca].add(x)
    conflictos = sum(len(valores) > 1 for valores in por_marca.values())
    limpios = [(marca, next(iter(v))) for marca, v in por_marca.items() if len(v) == 1]
    por_hora = defaultdict(list)
    por_dia = defaultdict(list)
    if agregacion == "diaria":
        por_fecha = defaultdict(set)
        for marca, v in limpios:
            por_fecha[marca.date()].add(v)
        for dia, valores in por_fecha.items():
            if len(valores) == 1:
                v = next(iter(valores))
                por_dia[dia].append((puntuar(componente, v), v))
            else:
                conflictos += 1
    elif agregacion == "horaria":
        for marca, v in limpios:
            por_hora[marca.replace(minute=0, second=0, microsecond=0)].append(v)
        for hora, valores in por_hora.items():
            por_dia[hora.date()].append((
                fmean(puntuar(componente, v) for v in valores), fmean(valores)))
    else:
        raise ValueError("Agregación debe ser horaria o diaria")
    dias = {}
    for dia, horas in por_dia.items():
        if agregacion == "diaria" or len(horas) >= MIN_HORAS:
            dias[dia] = (fmean(x[0] for x in horas), fmean(x[1] for x in horas))
    meses = []
    for mes, esperados in meses_esperados(inicio, fin).items():
        valores = [v for d, v in dias.items() if d.strftime("%Y-%m") == mes]
        n = len(valores)
        aceptado = n / esperados >= MIN_FRACCION_DIAS - 1e-12
        meses.append({
            "mes": mes, "dias_esperados": esperados, "dias_admisibles": n,
            "fraccion_dias": n / esperados, "admisible": aceptado,
            "puntaje_100": fmean(x[0] for x in valores) if valores else None,
            "valor_medio": fmean(x[1] for x in valores) if valores else None,
        })
    usados = [m for m in meses if m["admisible"]]
    admisible = len(usados) >= MIN_MESES
    return {
        "puntaje_100": fmean(m["puntaje_100"] for m in usados) if admisible else None,
        "valor_medio": fmean(m["valor_medio"] for m in usados) if admisible else None,
        "meses_usados": [m["mes"] for m in usados],
        "estado": ("Completo" if len(usados) == len(meses) else "Parcial temporal")
                  if admisible else "Meses insuficientes",
        "meses": meses, "observaciones_en_periodo": len(limpios),
        "filas_invalidas": rechazados, "marcas_en_conflicto": conflictos,
        "fuera_periodo": fuera_periodo,
    }


def elegir_estacion(lat, lon, estaciones):
    candidatas = [s for s in estaciones if any(
        v.get("puntaje_100") is not None for v in s["resumenes"].values())]
    candidatas = [(distancia(lat, lon, s["latitud"], s["longitud"]), s)
                  for s in candidatas]
    candidatas.sort(key=lambda x: (x[0], int(x[1]["codigo"])))
    if not candidatas or candidatas[0][0] > RADIO_ESTACION_M:
        return None
    d, s = candidatas[0]
    return {**s, "distancia_m": d}


def accesibilidad(nombre, lat, lon, catalogo, radio):
    if not catalogo:
        return {**combinar(nombre, {"distancia": None, "cantidad": None}),
                "estado": "Sin catálogo admisible", "cercano": None, "cantidad": None,
                "distancia_m": None}
    vecinos = [(distancia(lat, lon, x["latitud"], x["longitud"]), x)
               for x in catalogo]
    vecinos.sort(key=lambda x: (x[0], str(x[1]["codigo"])))
    d, cercano = vecinos[0]
    n = sum(v[0] <= radio for v in vecinos)
    sufijo = "paradero" if nombre == "paraderos" else "salud"
    cantidad = "cantidad_paraderos" if nombre == "paraderos" else "cantidad_salud"
    return {**combinar(nombre, {"distancia": puntuar("distancia_"+sufijo, d),
                               "cantidad": puntuar(cantidad, n)}),
            "estado": "Disponible", "distancia_m": d, "cantidad": n,
            "radio_m": radio, "cercano": cercano}
