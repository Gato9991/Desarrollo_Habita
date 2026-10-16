"""Índice HABITA provisional v0.1.0.

Fórmulas de demostración: no son estándares ambientales ni una evaluación
validada de habitabilidad o seguridad. Parámetros y pesos pueden cambiar.
Solo usa la biblioteca estándar de Python 3.10 o superior.
"""
from __future__ import annotations

import json
import math
from numbers import Real

VERSION_FORMULA = "0.1.0-provisional"
ADVERTENCIA = (
    "Fórmulas, límites y ponderaciones provisionales, sujetos a cambios y "
    "validación. El resultado no certifica habitabilidad ni seguridad. "
    "Usar datos de prueba no convierte el resultado en una medición real."
)
PESOS_VARIABLES = {
    "calidad_aire": 0.20,
    "confort_termico": 0.20,
    "paraderos": 0.20,
    "salud": 0.20,
    "amenazas_naturales": 0.20,
}


def limitar(valor: float) -> float:
    """Limita un número finito al intervalo [0, 100]."""
    valor = _validar("valor", valor)
    if valor is None:
        raise ValueError("valor no puede ser None")
    return max(0.0, min(100.0, valor))


def _validar(
    nombre: str, valor: float | None, minimo: float | None = None,
    maximo: float | None = None, entero: bool = False,
) -> float | None:
    if valor is None:
        return None
    if isinstance(valor, bool) or not isinstance(valor, Real):
        raise ValueError(f"{nombre}: debe ser un número o None")
    numero = float(valor)
    if not math.isfinite(numero):
        raise ValueError(f"{nombre}: debe ser finito")
    if minimo is not None and numero < minimo:
        raise ValueError(f"{nombre}: debe ser mayor o igual a {minimo}")
    if maximo is not None and numero > maximo:
        raise ValueError(f"{nombre}: debe ser menor o igual a {maximo}")
    if entero and not numero.is_integer():
        raise ValueError(f"{nombre}: debe ser una cantidad entera")
    return numero


def puntaje_mp25(valor: float | None) -> float | None:
    """MP2,5 en µg/m³. Límite de demostración: 100 µg/m³."""
    valor = _validar("mp25", valor, minimo=0)
    return None if valor is None else limitar(100 * (1 - valor / 100))


def puntaje_ozono(valor: float | None) -> float | None:
    """O₃ en µg/m³. Límite de demostración: 200 µg/m³."""
    valor = _validar("ozono", valor, minimo=0)
    return None if valor is None else limitar(100 * (1 - valor / 200))


def puntaje_temperatura(valor: float | None) -> float | None:
    """Temperatura en °C. Referencia de demostración: 22 °C."""
    valor = _validar("temperatura", valor)
    return None if valor is None else limitar(100 * (1 - abs(valor - 22) / 18))


def puntaje_humedad(valor: float | None) -> float | None:
    """Humedad relativa en %. Referencia de demostración: 50%."""
    valor = _validar("humedad", valor, minimo=0, maximo=100)
    return None if valor is None else limitar(100 * (1 - abs(valor - 50) / 50))


def puntaje_distancia_paradero(valor: float | None) -> float | None:
    """Distancia en metros al paradero más cercano, esté o no dentro de 500 m."""
    valor = _validar("distancia_paradero_m", valor, minimo=0)
    return None if valor is None else limitar(100 * (1 - valor / 1000))


def puntaje_cantidad_paraderos(valor: int | None) -> float | None:
    """Número de paraderos dentro de 500 m."""
    valor = _validar("paraderos_500m", valor, minimo=0, entero=True)
    return None if valor is None else limitar(valor * 10)


def puntaje_distancia_salud(valor: float | None) -> float | None:
    """Distancia en metros al establecimiento más cercano, aun fuera de 2 km."""
    valor = _validar("distancia_salud_m", valor, minimo=0)
    return None if valor is None else limitar(100 * (1 - valor / 5000))


def puntaje_cantidad_salud(valor: int | None) -> float | None:
    """Número de establecimientos de salud dentro de 2 km."""
    valor = _validar("establecimientos_2km", valor, minimo=0, entero=True)
    return None if valor is None else limitar(valor * 20)


def puntaje_amenaza(valor: float | None) -> float | None:
    """Invierte exposición simulada [0, 100]: menos exposición da más puntaje."""
    valor = _validar("exposicion", valor, minimo=0, maximo=100)
    return None if valor is None else 100 - valor


def _componente(nombre, valor, unidad, peso, funcion):
    return {
        "nombre": nombre, "valor": valor, "unidad": unidad,
        "peso_interno": peso, "puntaje": funcion(valor),
    }


def _combinar(componentes: list[dict]) -> dict:
    cobertura = sum(c["peso_interno"] for c in componentes if c["puntaje"] is not None)
    puntaje = (
        sum(c["peso_interno"] * c["puntaje"] for c in componentes
            if c["puntaje"] is not None) / cobertura
        if cobertura else None
    )
    return {
        "puntaje": puntaje,
        "cobertura": cobertura,
        "estado": "sin_datos" if not cobertura else (
            "completo" if math.isclose(cobertura, 1) else "incompleto"
        ),
        "componentes": componentes,
    }


def calcular_calidad_aire(
    mp25: float | None = None, ozono: float | None = None,
) -> dict:
    """Combina puntajes MP2,5 (70%) y O₃ (30%)."""
    return _combinar([
        _componente("mp25", mp25, "µg/m³", .70, puntaje_mp25),
        _componente("ozono", ozono, "µg/m³", .30, puntaje_ozono),
    ])


def calcular_confort_termico(
    temperatura: float | None = None, humedad: float | None = None,
) -> dict:
    """Combina temperatura (60%) y humedad relativa (40%)."""
    return _combinar([
        _componente("temperatura", temperatura, "°C", .60, puntaje_temperatura),
        _componente("humedad", humedad, "%", .40, puntaje_humedad),
    ])


def calcular_accesibilidad_paraderos(
    distancia_m: float | None = None, cantidad_500m: int | None = None,
) -> dict:
    """Combina distancia al más cercano (60%) y cantidad en 500 m (40%)."""
    return _combinar([
        _componente("distancia_m", distancia_m, "m", .60, puntaje_distancia_paradero),
        _componente("cantidad_500m", cantidad_500m, "paraderos", .40, puntaje_cantidad_paraderos),
    ])


def calcular_accesibilidad_salud(
    distancia_m: float | None = None, cantidad_2km: int | None = None,
) -> dict:
    """Combina distancia al más cercano (70%) y cantidad en 2 km (30%)."""
    return _combinar([
        _componente("distancia_m", distancia_m, "m", .70, puntaje_distancia_salud),
        _componente("cantidad_2km", cantidad_2km, "establecimientos", .30, puntaje_cantidad_salud),
    ])


def calcular_amenazas_naturales(
    remociones: float | None = None, fallas: float | None = None,
    volcanicas: float | None = None, inundaciones: float | None = None,
) -> dict:
    """Promedia cuatro puntajes favorables (25% cada uno). No certifica seguridad."""
    return _combinar([
        _componente(nombre, valor, "exposición simulada /100", .25, puntaje_amenaza)
        for nombre, valor in (
            ("remociones", remociones), ("fallas", fallas),
            ("volcanicas", volcanicas), ("inundaciones", inundaciones)
        )
    ])


def calcular_indice_habita(
    *, mp25: float | None = None, ozono: float | None = None,
    temperatura: float | None = None, humedad: float | None = None,
    distancia_paradero_m: float | None = None, paraderos_500m: int | None = None,
    distancia_salud_m: float | None = None, establecimientos_2km: int | None = None,
    remociones: float | None = None, fallas: float | None = None,
    volcanicas: float | None = None, inundaciones: float | None = None,
) -> dict:
    """Calcula HABITA sin redondeo intermedio.

    Datos ausentes: None. Cero es un valor válido.
    Cobertura: fracción [0, 1] del peso original con datos disponibles.
    Estado 'completo' significa disponibilidad total, no validación del índice.
    No consulta fuentes ni geocodifica: el llamador entrega las mediciones.
    """
    variables = {
        "calidad_aire": calcular_calidad_aire(mp25, ozono),
        "confort_termico": calcular_confort_termico(temperatura, humedad),
        "paraderos": calcular_accesibilidad_paraderos(distancia_paradero_m, paraderos_500m),
        "salud": calcular_accesibilidad_salud(distancia_salud_m, establecimientos_2km),
        "amenazas_naturales": calcular_amenazas_naturales(
            remociones, fallas, volcanicas, inundaciones
        ),
    }
    cobertura = sum(PESOS_VARIABLES[n] * v["cobertura"] for n, v in variables.items())
    for nombre, variable in variables.items():
        variable["peso_base"] = PESOS_VARIABLES[nombre]
        variable["peso_efectivo"] = variable["peso_base"] * variable["cobertura"]
        variable["participacion_final"] = (
            variable["peso_efectivo"] / cobertura if cobertura else 0.0
        )
        variable["aporte_al_indice"] = (
            variable["participacion_final"] * variable["puntaje"]
            if variable["puntaje"] is not None else 0.0
        )
    indice = sum(v["aporte_al_indice"] for v in variables.values()) if cobertura else None
    return {
        "version_formula": VERSION_FORMULA,
        "indice_habita": indice,
        "cobertura": cobertura,
        "cobertura_porcentaje": cobertura * 100,
        "estado": "sin_datos" if not cobertura else (
            "completo" if math.isclose(cobertura, 1) else "provisional"
        ),
        "metodologia_provisional": True,
        "advertencia": ADVERTENCIA,
        "variables": variables,
    }


if __name__ == "__main__":
    # Ejemplo SB-01: valores ficticios, no mediciones de San Bernardo.
    resultado = calcular_indice_habita(
        mp25=35, ozono=60, temperatura=25, humedad=45,
        distancia_paradero_m=150, paraderos_500m=8,
        distancia_salud_m=800, establecimientos_2km=3,
        remociones=20, fallas=30, volcanicas=0, inundaciones=40,
    )
    print(json.dumps(resultado, ensure_ascii=False, indent=2, allow_nan=False))
