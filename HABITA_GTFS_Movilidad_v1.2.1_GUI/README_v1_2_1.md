# 🚌 Analizador GTFS de Accesibilidad de Transporte — San Bernardo

**Versión 1.2.1** — Corrige los defectos metodológicos y de robustez confirmados
de la v1.2 (ver `CHANGELOG_v1_2_1.md`). No reconstruye el proyecto desde
cero: conserva íntegramente las correcciones de v1.2 (lectura ZIP/carpeta,
filtrado por calendario, offsets, `end_time` exclusivo, distancias
rotuladas como geodésicas, coordenadas correctas, rendimiento).

---

## ⚠️ Cambios importantes respecto a v1.2

- **El headway de la ruta 511 (sentido 1) en `PD561`, punta tarde,
  cambió de `11.51 min` (sin offset, cálculo incorrecto) a
  `10.96 min` (con offset).** El valor de v1.2 no desplazaba el bloque
  de `frequencies.txt` hasta la parada usando `offset_s`; comparaba la
  ventana punta contra el bloque tal como aparece en el primer punto
  del patrón. Ver detalle fila por fila en `CHANGELOG_v1_2_1.md §1` y
  en `GTFS_ANALYZER_GUIDE_v1_2_1.md §1`.
- **`area_analysis` ya no reporta un solo `total_departures` ambiguo.**
  Ahora separa `stop_departure_events` (eventos de paso, puede repetir
  un mismo vehículo si cruza varias paradas del radio) de
  `unique_trip_instances` (cada instancia de viaje, una sola vez),
  con desglose por `route_id`.
- **Nueva distinción `nearest_physical_stop` / `nearest_active_stop`.**
  Antes se elegía la parada geográficamente más cercana y recién
  después se comprobaba si tenía viajes activos; ahora ambas nociones
  son explícitas y pueden diferir.
- **`pickup_type == 1` se excluye de toda cuenta de salidas.** Antes
  no se filtraba en ningún punto del programa.
- **`frequencies.txt` ya no es obligatorio.** Un feed 100% de horario
  absoluto ahora carga sin error.
- **`calendar.txt` y `calendar_dates.txt` se aceptan en cualquier
  combinación válida** (uno, otro, o ambos); antes `calendar.txt` era
  siempre obligatorio.
- **`generated_at` ahora usa la zona horaria declarada en
  `agency.txt`** (vía `zoneinfo`), o se renombra a `generated_at_utc`
  si no hay ninguna zona declarada, en vez de mezclar ambas sin avisar.
- **Haversine se calcula exactamente una vez por `analyze()`** (antes
  se recalculaba una vez por cada uno de los tres análisis internos).
- **Serialización JSON estricta** (`allow_nan=False`): cualquier `NaN`
  o tipo NumPy/Pandas que sobreviviera a la limpieza produce un error
  explícito en vez de un JSON técnicamente inválido.

Los dos casos territoriales oficiales del ejemplo y la documentación son
(sin cambios en sus coordenadas respecto de la entrega anterior):

```
Plaza de Armas de San Bernardo:           lat=-33.5922368  lon=-70.704706
Estadio Nacional Julio Martínez Prádanos: lat=-33.4644994  lon=-70.6112756
```

---

## 📦 Qué contiene

```
gtfs_analyzer_v1_2_1.py            ← Programa principal (ejecutable)
test_gtfs_analyzer_v1_2_1.py       ← Pruebas automatizadas (pytest, 38 casos)
EJEMPLO_SAN_BERNARDO_v1_2_1.py     ← Las dos coordenadas definitivas
GTFS_ANALYZER_GUIDE_v1_2_1.md      ← Guía técnica: metodología y fórmulas
CHANGELOG_v1_2_1.md                ← Cada defecto de v1.2 y su corrección
README_v1_2_1.md                   ← Este archivo
```

No incluye datos GTFS: debes indicar tu propia copia del feed con `--gtfs`.

---

## 🖥️ Requisitos

- Python 3.9 o superior (usa `zoneinfo`, incluido desde 3.9; probado en 3.12).
- Paquetes: `pandas`, `numpy`, y `pytest` solo si vas a correr las pruebas.

```bash
python -m pip install pandas numpy pytest
```

---

## 🚀 Uso

```bash
python3 gtfs_analyzer_v1_2_1.py \
  --gtfs /ruta/a/GTFS_20260829.zip \
  --date 2026-09-23 \
  --lat -33.5922368 \
  --lon -70.704706 \
  --name "Plaza de Armas - San Bernardo" \
  --export-json
```

`--gtfs` acepta carpeta extraída o `.zip` (incluso con los `.txt` dentro
de una subcarpeta del ZIP). `--out` se crea automáticamente si no
existe.

### Argumentos

| Argumento | Obligatorio | Descripción |
|---|---|---|
| `--gtfs` | Sí | Carpeta GTFS extraída o archivo `.zip` |
| `--date` | Sí | Fecha ISO `YYYY-MM-DD` a consultar |
| `--lat` / `--lon` | Sí (salvo `--interactive`) | Coordenadas de la ubicación |
| `--name` | No | Nombre descriptivo para el reporte y el archivo exportado |
| `--export-json` | No | Además de imprimir el JSON, lo guarda en disco |
| `--out` | No | Carpeta de salida del JSON exportado (se crea si falta) |
| `--interactive` | No | Modo interactivo: pide coordenadas por consola |

### Ejemplo con las dos coordenadas definitivas

```bash
python3 EJEMPLO_SAN_BERNARDO_v1_2_1.py --gtfs /ruta/al/feed --date 2026-09-23
```

---

## 📊 Esquema del JSON de salida

```json
{
  "metadata": {
    "feed_version": "V167.20260829",
    "analysis_date": "2026-09-23",
    "active_service_ids": ["L", "LJ"],
    "timezone": "America/Santiago",
    "generated_at": "2026-09-23T10:32:07-03:00"
  },
  "location": {"name": "...", "lat": 0.0, "lon": 0.0},
  "nearest_physical_stop": {},
  "nearest_active_stop": {
    "service_window": {},
    "peak_departures": {},
    "headway_by_route_direction": []
  },
  "closest_stop_analysis": {},
  "area_analysis": {
    "radius_500m": {
      "stop_points": 0,
      "active_routes": [],
      "morning_peak": {
        "stop_departure_events": 0,
        "unique_trip_instances": 0,
        "by_route": {}
      },
      "afternoon_peak": {}
    },
    "radius_800m": {}
  },
  "limitations": []
}
```

- `nearest_physical_stop`: la parada abordable geográficamente más
  cercana, exista o no servicio activo ese día.
- `nearest_active_stop`: la parada abordable más cercana que además
  tiene al menos un viaje de un `service_id` activo esa fecha.
  `same_as_nearest_physical_stop` indica si coinciden.
- `closest_stop_analysis`: alias de compatibilidad con v1.2, apunta al
  mismo contenido que `nearest_active_stop`.
- `headway_by_route_direction[].headway.<ventana>` reporta, cuando
  aplica: `scheduled_departures` (salidas realmente generadas en la
  ventana), `weighted_block_headway_min` (headway ponderado por
  solapamiento, con el bloque ya desplazado a la parada),
  `effective_interval_from_generated_departures_min` (intervalo
  promedio entre la primera y la última salida generada), y
  `concurrent_patterns_detected` (si dos `trip_id` distintos del mismo
  recorrido y sentido se solapan en el tiempo dentro de la ventana).
- `area_analysis.*.morning_peak` / `afternoon_peak`:
  `stop_departure_events` (eventos de paso, puede repetir un vehículo)
  vs. `unique_trip_instances` (cada instancia de viaje, una vez), con
  desglose `by_route`.

---

## 🧪 Ejecutar las pruebas

```bash
python -m pytest test_gtfs_analyzer_v1_2_1.py -v
```

**Resultado real de la última ejecución contra el feed de San Bernardo
(`V167.20260829`):**

```
38 passed in 95.6s
```

0 fallidas, 0 omitidas, 0 advertencias. Las pruebas que requieren el
feed real se controlan con la variable de entorno `GTFS_TEST_PATH`
(default: `/mnt/user-data/uploads`); si el feed no está disponible en
esa ruta, esas pruebas específicas se omiten automáticamente
(`SKIPPED`), y esta entrega **no** declara como ejecutadas pruebas que
en los hechos quedaron `SKIPPED`.

```bash
GTFS_TEST_PATH=/ruta/al/feed python -m pytest test_gtfs_analyzer_v1_2_1.py -v
```

---

## 🏘️ Aplicación estratégica: subíndice de Movilidad y Accesibilidad de HABITA

Esta sección traduce lo que el analizador ya entrega en algo útil para
HABITA. **No describe una funcionalidad implementada**: es una capa de
interpretación sobre los outputs actuales, más una propuesta técnica
inicial para calibrar un puntaje futuro. El programa **no calcula
ningún puntaje HABITA de 0 a 100**; eso queda explícitamente fuera del
alcance de esta versión.

### Qué puede medir

Este módulo puede convertirse en el **subíndice de Movilidad y
Accesibilidad de HABITA**. No sirve para construir todo el índice
HABITA, pero sí aporta una dimensión objetiva, repetible y comparable
entre propiedades:

| Dimensión | Dato del analizador | Interpretación |
|---|---|---|
| Proximidad | Distancia a la parada activa más cercana (`nearest_active_stop`) | Facilidad inmediata de acceso |
| Diversidad | Recorridos activos dentro de 800 m (`area_analysis.radius_800m.active_routes_count`) | Cantidad de alternativas disponibles |
| Intensidad | Viajes únicos en punta (`unique_trip_instances`) | Oferta programada durante horas críticas |
| Cobertura horaria | Primera y última salida (`service_window`) | Cuánto dura el servicio durante el día |
| Cobertura territorial | Paradas en 500 y 800 m (`stop_points`) | Densidad de infraestructura cercana |
| Frecuencia | Headway por recorrido y sentido (`headway_by_route_direction`) | Tiempo programado entre vehículos |

Una ficha para el Estadio Nacional, con los datos ya producidos por
este analizador, podría mostrarse así:

> **Movilidad programada:** parada activa a 308 m, 19 recorridos dentro
> de 800 m, 517 viajes únicos durante la punta mañana y servicio
> disponible después de medianoche.

Eso es comprensible para alguien que evalúa comprar o arrendar, sin que
el analizador tenga que decidir todavía si eso es "bueno" o "malo" en
una escala.

### Qué variables usaría para puntuar (propuesta técnica, no decisión cerrada)

Una propuesta técnica inicial para combinar estas variables en un solo
número —**los pesos finales son decisión del equipo responsable de
HABITA, no de este analizador**— sería:

$$
M = 0.35P + 0.30I + 0.20D + 0.15H
$$

Donde:

| Componente | Variable | Peso inicial propuesto |
|---|---|---:|
| \(P\) | Proximidad a la parada activa | 35% |
| \(I\) | Intensidad de viajes únicos | 30% |
| \(D\) | Diversidad de recorridos activos | 20% |
| \(H\) | Amplitud horaria del servicio | 15% |

Deliberadamente **no** se propone el número de paradas (`stop_points`)
como componente principal, porque muchas paradas pueden pertenecer al
mismo recorrido y dar una ilusión de abundancia de oferta que no existe.

### El hallazgo importante de este análisis

Con el feed `V167.20260829`, fecha `2026-09-23`, radio 800 m, punta
mañana (07:00–09:00):

- **Plaza de Armas de San Bernardo**: 3.077 eventos de salida, 554
  viajes únicos.
- **Estadio Nacional Julio Martínez Prádanos**: 1.802 eventos de
  salida, 517 viajes únicos.

Los eventos aparentan una diferencia enorme (3.077 vs. 1.802, un
70% más), pero los viajes únicos solo difieren cerca de un **7%** (554
vs. 517). Esto demuestra en datos reales lo que
`GTFS_ANALYZER_GUIDE_v1_2_1.md §2` ya documenta técnicamente:
`stop_departure_events` cuenta un mismo viaje una vez por cada parada
del área que atraviesa, mientras que `unique_trip_instances` lo cuenta
una sola vez.

Por eso, para cualquier futuro puntaje:

- **Para el índice**: usar principalmente `unique_trip_instances` como
  variable de intensidad.
- **Para diagnóstico operativo**: conservar `stop_departure_events`
  como indicador de densidad de paso, sin usarlo para puntuar.
- **Nunca sumar ambas como si fueran variables independientes.** Hacerlo
  premiaría dos veces la misma oferta: bonito para una lámina de
  marketing, metodológicamente insostenible para un índice que alguien
  va a usar para decidir dónde comprar o invertir.

### Lo que todavía falta antes de crear una nota 0–100

No se debe inventar que "19 recorridos = 85 puntos". Antes de puntuar
nada hace falta una referencia territorial:

1. Ejecutar el análisis sobre muchas coordenadas del Gran Santiago, no
   solo las dos de este ejemplo.
2. Incluir comunas centrales, periféricas y sectores con cobertura baja.
3. Analizar un día laboral, un sábado y un domingo (el analizador ya
   distingue `service_id` por fecha; falta el muestreo territorial).
4. Calcular percentiles para cada variable sobre esa muestra.
5. Convertir cada resultado a una escala comparable de 0 a 100.
6. Validar que los extremos (zonas sin ningún servicio, o con
   frecuencias excepcionalmente altas) no deformen la escala.

Por ejemplo, como referencia de diseño (no como regla ya calibrada):

- Percentil 90 o superior → cerca de 100 puntos.
- Mediana metropolitana → cerca de 50.
- Percentil 10 o inferior → cerca de 0.

Así la puntuación se basaría en cómo se compara una ubicación con el
territorio, no en umbrales fijados arbitrariamente sobre dos casos de
ejemplo.

### Límites que HABITA debe declarar explícitamente

Este módulo mide **oferta de transporte público programada**, y
**nada más**. En particular, no mide:

- Puntualidad real del servicio.
- Congestión de las vías.
- Cancelaciones de viajes.
- Seguridad del trayecto o de las paradas.
- Calidad física de la infraestructura de parada.
- Distancia caminable real (sigue siendo geodésica, ver más arriba).
- Tiempo real de viaje puerta a puerta.

Por eso, si HABITA adopta este subíndice, el nombre correcto para
mostrarlo al usuario final sería algo como:

> **Accesibilidad a transporte público programado**

y no "calidad del transporte" ni "tiempo real de viaje": esa precisión
verbal es la diferencia entre una plataforma que un usuario puede
auditar y una que solo imprime un número sin poder explicar de dónde
sale.

**Conclusión**: el analizador ya entrega la materia prima para un
subíndice de movilidad defendible. Lo que sigue no es programar más
funciones al azar, sino construir la muestra territorial con la que se
calibrará, más adelante, una escala 0–100.

---

## ⚠️ Limitaciones vigentes (no resueltas en esta versión, por diseño)

- **Sin distancia caminable real**: `geodesic_distance_m` sigue siendo
  Haversine en línea recta; no se integró ninguna API de enrutamiento
  externa (fuera de alcance según la sección 15 del encargo).
- **Sin puntaje HABITA**: esta versión entrega métricas auditables, no
  un índice compuesto (sección 15).
- **Sin GTFS-Realtime**: todo proviene del feed estático
  `V167.20260829`, vigente `2026-08-29` a `2026-12-31`.
- **`exact_times=0` sigue siendo una estimación**, nunca una
  observación real del vehículo (sección 15).
- **`concurrent_patterns_detected`** identifica cuándo dos patrones de
  un mismo recorrido y sentido se solapan en el tiempo, pero no calcula
  la frecuencia combinada real de ambos (eso requeriría fusionar los
  trenes de llegadas de cada patrón, fuera del alcance de esta
  corrección puntual). Se documenta explícitamente en el JSON cuando
  ocurre.

---

## ❓ Problemas conocidos y cómo reportarlos

Si encuentras un resultado que no coincide con lo esperado:
1. Verifica que la coordenada esté dentro de la Región Metropolitana.
2. Confirma que la fecha esté dentro de la vigencia del feed que usaste.
3. Describe el problema con la coordenada, fecha y salida obtenida,
   incluyendo si corresponde a `nearest_physical_stop` o
   `nearest_active_stop`.
