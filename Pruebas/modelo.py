"""Fórmulas puras de HABITA 0.2.0; propuesta en evaluación, no versión oficial."""
from __future__ import annotations

from collections import defaultdict
from datetime import date, datetime, timedelta
from math import asin, cos, isfinite, radians, sin, sqrt
from statistics import fmean

VERSION = "0.3.0-prueba.1"
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
        "estado": ("Completo en componentes" if cobertura >= 1 - 1e-12 else "Parcial") if publicable
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


# Aire: resumen del último año común disponible. Son 365 fechas esperadas.
INICIO_AIRE = date(2025, 10, 14)
FIN_AIRE = date(2026, 10, 13)
MIN_FRACCION_DIAS_ANUAL = .70
# Referencia de conversión del prototipo; no es temperatura/presión observada.
O3_REFERENCIA_TEMP_C = 25.0
O3_REFERENCIA_PRESION_PA = 101_325.0
O3_MASA_MOLAR_G_MOL = 48.0
CONSTANTE_GASES = 8.31446261815324
O3_PPB_A_UG_M3 = (
    O3_REFERENCIA_PRESION_PA * O3_MASA_MOLAR_G_MOL
    / (CONSTANTE_GASES * (273.15 + O3_REFERENCIA_TEMP_C)) * 1e-3
)


def concentracion_normalizada(componente, valor, unidad):
    """Misma escala µg/m³ del prototipo; O3 ppb usa referencia declarada."""
    x = numero(valor)
    if x is None or x < 0:
        return None
    if componente == "mp25" and unidad == "ug/m3":
        return x
    if componente == "o3" and unidad == "ppb":
        return x * O3_PPB_A_UG_M3
    if componente == "o3" and unidad == "ug/m3":
        return x
    return None


def resumir_aire_anual(observaciones, componente, unidad,
                       inicio=INICIO_AIRE, fin=FIN_AIRE):
    """Media de puntajes de registros diarios; no un promedio anual oficial SINCA.

    Espera (fecha-hora, valor, estado) o (fecha-hora, valor). Cada fecha pesa
    una vez. No usa promedios mensuales ni mezcla años históricos ajenos a la
    ventana. Excluye por sí mismo los estados no validados.
    """
    if fin < inicio:
        raise ValueError("Ventana anual invertida")
    dias = defaultdict(list)
    rechazados = 0
    fuera_periodo = 0
    no_validados = 0
    for observacion in observaciones:
        marca, valor = observacion[:2]
        estado = observacion[2] if len(observacion) > 2 else "sin_estado"
        if not inicio <= marca.date() <= fin:
            fuera_periodo += 1
            continue
        if estado == "no_validados":
            no_validados += 1
            continue
        if estado not in ("validados", "preliminares", "sin_estado"):
            rechazados += 1
            continue
        x = numero(valor)
        normalizado = concentracion_normalizada(componente, x, unidad)
        if normalizado is None:
            rechazados += 1
            continue
        dias[marca.date()].append((x, normalizado, estado))
    aceptados = []
    conflictos = 0
    estados = defaultdict(int)
    for dia, valores in sorted(dias.items()):
        # Validado tiene prioridad sobre preliminar si aparecen ambos.
        orden = {"validados": 0, "preliminares": 1, "sin_estado": 2}
        prioridad = min(orden.get(x[2], 9) for x in valores)
        mejores = [x for x in valores if orden.get(x[2], 9) == prioridad]
        if len({x[0] for x in mejores}) != 1:
            conflictos += 1
            continue
        x, normalizado, estado = mejores[0]
        aceptados.append((dia, x, normalizado, puntuar(componente, normalizado)))
        estados[estado] += 1
    esperados = (fin-inicio).days + 1
    n = len(aceptados)
    suficiente = n/esperados >= MIN_FRACCION_DIAS_ANUAL - 1e-12
    return {
        "tipo_resumen": "media_anual_de_puntajes_diarios",
        "puntaje_100": fmean(x[3] for x in aceptados) if suficiente else None,
        "valor_medio": fmean(x[1] for x in aceptados) if aceptados else None,
        "valor_medio_normalizado_ug_m3": fmean(x[2] for x in aceptados) if aceptados else None,
        "unidad_original": unidad,
        "periodo": {"inicio": inicio.isoformat(), "fin": fin.isoformat()},
        "dias_esperados": esperados, "dias_admisibles": n,
        "dias_ausentes_o_excluidos": esperados-n,
        "fraccion_dias": n/esperados, "min_fraccion_dias": MIN_FRACCION_DIAS_ANUAL,
        "estado": ("Completo" if n == esperados else "Parcial temporal")
                  if suficiente else "Cobertura anual insuficiente",
        "estados_aceptados": dict(estados),
        "filas_invalidas": rechazados, "dias_en_conflicto": conflictos,
        "no_validados_excluidos": no_validados,
        "fuera_periodo": fuera_periodo,
        "meses_usados": sorted({x[0].strftime("%Y-%m") for x in aceptados}),
        "meses": [],
        "conversion": ({
            "origen": "ppb", "destino": "ug/m3",
            "factor": O3_PPB_A_UG_M3, "temperatura_referencia_c": O3_REFERENCIA_TEMP_C,
            "presion_referencia_pa": O3_REFERENCIA_PRESION_PA,
            "nota": "Referencia del cálculo provisional, no condiciones atmosféricas medidas.",
        } if componente == "o3" and unidad == "ppb" else None),
    }
