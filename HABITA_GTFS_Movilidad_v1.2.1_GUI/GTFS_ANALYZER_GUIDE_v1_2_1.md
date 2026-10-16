# GTFS Analyzer v1.2.1 — Guía técnica

Esta guía documenta solo lo que cambió metodológicamente respecto de
`GTFS_ANALYZER_GUIDE_v1_2.md`. Todo lo no mencionado aquí (carga del
feed, `get_active_services`, Haversine, `end_time` exclusivo, formato
de horas ≥24) sigue igual que en v1.2.

---

## 1. Headway ponderado, ahora desplazado por `offset_s` (corrección crítica 1)

### El error de v1.2

`_weighted_headway_for_group` intersectaba la ventana punta consultada
(p. ej. `07:00–09:00`) directamente contra `fr["start_s"]` /
`fr["end_s"]` de `frequencies.txt`, es decir, contra el horario del
bloque **en el primer punto del patrón del viaje**, no en la parada
consultada. Para una parada intermedia o final con offset grande, eso
compara ventanas que no corresponden.

### La corrección

```python
block_start_at_stop = fr["start_s"] + row.offset_s
block_end_at_stop   = fr["end_s"]   + row.offset_s

s = max(block_start_at_stop, s0)
e = min(block_end_at_stop, s1)
```

`headway_secs` no cambia; lo que cambia es **qué bloque está vigente**
en la parada dentro de la ventana consultada.

### Evidencia: recorrido 511 (sentido 1) en `PD561`, Estadio Nacional (día laboral)

Con offset aplicado, los bloques de `511-R-L-B00..B11` relevantes para
la ventana punta tarde quedan así en `PD561` (offset propio de cada
bloque, calculado desde su propio `departure_time` en la parada menos
el despacho de su primera parada):

| Bloque | Horario en patrón | Offset (s) | Horario en `PD561` |
|---|---|---:|---|
| B07 | 14:00–16:30 | 2500 | 14:41:40–17:11:40 |
| B08 | 16:30–18:30 | 2545 | 17:12:25–19:12:25 |
| B09 | 18:30–20:30 | 2153 | 19:05:53–21:05:53 |

(tabla completa con los 11 bloques en el código, método
`_weighted_headway_for_group`). Al intersectar estos bloques
desplazados con la ventana `17:00–19:00`, solo `B07` (su tramo final,
700 s de solapamiento, `headway_secs=692`) y `B08` (6.455 s de
solapamiento, `headway_secs=654`) caen dentro de la ventana —`B09`
empieza después de las 19:00 una vez desplazado, así que no aporta—:

```
tarde (17:00-19:00) SIN offset (v1.2, incorrecto): 11.5083 min ≈ 11.51 min
tarde (17:00-19:00) CON offset (v1.2.1, corregido): 10.9620 min ≈ 10.96 min
```

Con 12 salidas generadas en la ventana (mismo conteo que ya reportaba
la generación de salidas individuales, que ya sumaba `offset_s`
correctamente desde v1.2: el error estaba específicamente en el
ponderado del headway, no en la lista de salidas). Reproducible con
`test_route_511_headway_with_offset_matches_expected`. El caso
originalmente documentado aquí usaba otra parada de control que fue
retirada del proyecto (ver `CHANGELOG_v1_2_1.md`); la propiedad
metodológica verificada es idéntica.

### Tres magnitudes, no una

```json
{
  "scheduled_departures": 12,
  "distinct_trip_id_patterns": 11,
  "concurrent_patterns_detected": false,
  "weighted_block_headway_min": 10.96,
  "effective_interval_from_generated_departures_min": 10.04
}
```

- `weighted_block_headway_min`: promedio del `headway_secs` declarado,
  ponderado por cuánto de cada bloque se solapa con la ventana. Es lo
  que el operador *planificó* como intervalo entre buses en ese tramo
  horario.
- `effective_interval_from_generated_departures_min`: `(última salida
  generada − primera) / (n_salidas − 1)`, sobre las salidas realmente
  instanciadas dentro de la ventana. Puede diferir del anterior cuando
  la ventana consultada solo captura una fracción de un bloque (como en
  este caso: la ventana tarde cruza el límite entre `B07` y `B08`,
  dejando fuera una parte de cada uno).
- `concurrent_patterns_detected`: `true` si dos `trip_id` **distintos**
  del mismo `route_id`+`direction_id` tienen bloques que se solapan en
  el tiempo dentro de la ventana (servicios simultáneos). En ese caso
  se agrega una `note` explícita: el promedio ponderado NO equivale a
  la frecuencia combinada real (que sería más corta, por la
  intercalación de ambos servicios). Para el recorrido 511 en `PD561`
  los bloques relevantes de la punta tarde son secuenciales (`B07`
  termina antes de que empiece la parte de `B08` que cae en la
  ventana), por lo que `concurrent_patterns_detected=false`. En la
  punta **mañana** de esa misma parada sí se observa un caso real de
  `concurrent_patterns_detected=true` (recorrido `508`, ver el JSON de
  ejemplo del Estadio Nacional), donde dos patrones de un mismo sentido
  se solapan efectivamente en el tiempo.

---

## 2. Eventos de parada vs. instancias únicas de viaje (corrección crítica 2)

### El error de v1.2

`area_analysis` sumaba una salida generada por cada fila de
`stop_times` dentro del radio. Un mismo viaje que cruza 5 paradas
dentro de 800 m se contaba 5 veces bajo el nombre ambiguo
`total_departures`.

### La corrección

Cada salida generada lleva una identidad reproducible:

- viaje con `frequencies.txt`: `(trip_id, frequency_block_index,
  departure_instance_index)`.
- viaje con horario absoluto: `(trip_id, "scheduled")`.

```json
{
  "stop_departure_events": 3077,
  "unique_trip_instances": 554,
  "by_route": {
    "G09": {"stop_departure_events": 42, "unique_trip_instances": 9}
  }
}
```

- `stop_departure_events`: cuenta cada (parada, salida) dentro del
  radio y la ventana. Sirve como indicador de **densidad operacional**
  alrededor del punto (cuántas veces "pasa un bus" contando cada punto
  de parada).
- `unique_trip_instances`: cuenta cada instancia de viaje **una sola
  vez**, sin importar cuántas paradas del radio cruce. Representa mejor
  la **oferta vehicular no duplicada**.

Ninguna de las dos se llama "cantidad de buses" sin esta aclaración,
tal como exige el encargo.

### Verificación con controles independientes (feed real, miércoles laboral)

| Ubicación | Ventana | `stop_departure_events` | `unique_trip_instances` |
|---|---|---:|---:|
| Plaza de Armas | 07:00–09:00 | 3.077 | 554 |
| Plaza de Armas | 17:00–19:00 | 2.772 | 489 |
| Estadio Nacional | 07:00–09:00 | 1.802 | 517 |
| Estadio Nacional | 17:00–19:00 | 1.835 | 527 |

Los cuatro coinciden exactamente con los controles del encargo
(`test_controles_eventos_e_instancias`). Además, la deduplicación se
prueba de forma aislada y sencilla: un viaje que cruza 3 paradas del
radio produce 3 eventos y 1 instancia única
(`test_single_trip_crossing_three_stops_counts_one_unique_instance`).

---

## 3. Haversine calculado una sola vez por `analyze()`

`analyze()` ahora computa `distances = self.compute_distances(lat, lon)`
una sola vez, y la pasa explícitamente a `nearest_physical_stop`,
`nearest_active_stop_position`, `_stop_detail` y `area_analysis` (dos
veces, para 500 m y 800 m). Ninguna de estas funciones vuelve a llamar
`compute_distances` internamente cuando se les pasa la serie ya
calculada. Verificado con `unittest.mock.patch` contando las llamadas
(`test_compute_distances_called_once_per_analyze`): exactamente 1
llamada por `analyze()` completo, aunque internamente se resuelvan la
parada más cercana Y dos radios distintos.

---

## 4. `frequencies.txt` opcional

`REQUIRED_FILES` ya no incluye `frequencies.txt`. Si el archivo falta
del feed (carpeta o ZIP), se crea internamente un DataFrame vacío con
las columnas esperadas y `_freq_by_trip = {}`; todos los viajes caen
entonces en la rama de horario absoluto (`stop_times.departure_time`
literal), que ya existía desde v1.1/v1.2 para viajes sin entrada de
frecuencia. Probado con un feed sintético sin el archivo
(`test_feed_without_frequencies_file`).

---

## 5. Calendario: combinaciones válidas de GTFS

GTFS permite tres configuraciones válidas de calendario:

1. Solo `calendar.txt` (patrón semanal, sin excepciones).
2. `calendar.txt` + `calendar_dates.txt` (patrón semanal con
   excepciones puntuales) — configuración del feed real de la RM.
3. Solo `calendar_dates.txt` (cada `service_id` se define
   exclusivamente por fechas explícitas con `exception_type=1`; sin
   patrón semanal implícito).

Si **ninguno** de los dos archivos existe, se lanza `GTFSDataError`
explícito ("no hay ninguna definición de servicio disponible"), en vez
de asumir silenciosamente que no hay servicio.

Las tres configuraciones válidas y el caso de error están probadas
(`test_feed_with_both_calendar_files`,
`test_feed_with_only_calendar_dates`,
`test_feed_without_any_calendar_file_raises`). Nota: cuando el feed
sólo trae `calendar_dates.txt`, un `service_id` sin una fila explícita
de alta (`exception_type=1`) para la fecha consultada se considera
inactivo ese día, ya que no existe patrón semanal del cual derivarlo.

---

## 6. `nearest_physical_stop` vs. `nearest_active_stop`

- **`nearest_physical_stop`**: la parada abordable (ver §7) más cercana
  geográficamente, exista o no algún viaje activo esa fecha.
- **`nearest_active_stop`**: la parada abordable más cercana que además
  tiene al menos un viaje de un `service_id` activo esa fecha
  (`_active_stop_ids`).

Cuando la parada física más cercana no tiene servicio activo ese día
(por ejemplo, una parada exclusivamente de fin de semana consultada un
lunes), `nearest_active_stop` busca la siguiente más cercana que sí lo
tenga, en vez de reportar "sin servicio" cuando en realidad existe una
alternativa cercana. `same_as_nearest_physical_stop` indica
explícitamente si ambas coinciden. Para Plaza de Armas y Estadio
Nacional, en la fecha de control, ambas coinciden (`PG1599` y `PD561`
respectivamente), preservando los controles espaciales exigidos.
Probado con un fixture donde la parada física está inactiva entre
semana y una segunda parada sí tiene servicio
(`test_nearest_physical_inactive_second_stop_active`).

---

## 7. `pickup_type` y `location_type`: accesibilidad real de abordaje

- **`pickup_type == "1"`** (sin abordaje permitido en esa parada para
  ese viaje) se excluye de: generación de salidas, ventana de
  servicio, headway y conteos de `area_analysis`. `pickup_type` vacío
  o `"0"` se trata como abordaje permitido. Otros valores GTFS (`"2"`
  = coordinar con la agencia, `"3"` = coordinar con el conductor) se
  tratan como abordaje permitido por defecto, pero si aparecen en el
  feed se documentan explícitamente en `limitations`
  (`self._other_pickup_types`). El offset de cada fila (`offset_s`)
  sigue calculándose sobre **todas** las filas, boardables o no: el
  offset describe la estructura horaria del viaje, no la posibilidad
  de abordar en ese punto.
- **`location_type`**: solo se consideran candidatas a "parada
  abordable" las filas de `stops.txt` con `location_type` vacío o
  `"0"` (parada/plataforma). Se excluyen estaciones agregadas (`"1"`),
  entradas/salidas (`"2"`), nodos genéricos (`"3"`) y zonas de
  embarque reportadas aparte (`"4"`), siguiendo la recomendación del
  encargo de "priorizar `location_type` vacío o 0".
- **Presencia real en `stop_times.txt`**: además del `location_type`,
  solo se consideran abordables las paradas que aparecen efectivamente
  en al menos una fila de `stop_times.txt` (5.990 filas de
  `stops.txt` del feed real no tienen ninguna fila de `stop_times.txt`
  asociada: son mayormente entradas y nodos de estaciones).

En Plaza de Armas de San Bernardo, todas las paradas dentro de 800 m
tienen `location_type` vacío y aparecen en `stop_times.txt`, así que
estas protecciones no alteran ese resultado ya validado. El Estadio
Nacional es un caso más exigente y más útil como evidencia real: dentro
de 800 m existen paradas con `location_type="1"` (estación agregada) y
`"2"` (entrada), además de 3 paradas de `stops.txt` sin ninguna fila en
`stop_times.txt`. Ninguna de ellas resultó ser más cercana que `PD561`
(308,0 m), así que el resultado de `nearest_physical_stop` no cambió —
pero es precisamente el caso real que esta protección existe para
cubrir: si una entrada de estación hubiera estado más cerca que `PD561`,
sin este filtro el programa la habría reportado como si fuera la parada
abordable más próxima. `stop_points` (cobertura física en 500/800 m)
sigue contando **todas** las filas de `stops.txt` dentro del radio,
boardables o no, porque es una métrica de densidad de infraestructura,
no de oportunidades de abordaje; por eso su valor no cambia con estas
protecciones.
Probado con fixtures sintéticos donde una fila tiene `pickup_type=1`
(`test_pickup_type_1_excluded_from_departures`) y donde una "parada"
es en realidad una entrada de estación
(`test_non_boardable_location_type_ignored`).

---

## 8. Zona horaria coherente

- Si `agency.txt` declara exactamente una `agency_timezone`, se usa esa
  zona con `zoneinfo.ZoneInfo` para calcular `generated_at` (con offset
  real de esa fecha, incluyendo horario de verano si aplica).
- Si declara **más de una** zona distinta entre agencias, se usa la
  primera en orden alfabético y se documenta la ambigüedad en
  `limitations` (`_timezone_ambiguous`).
- Si **no hay ninguna** zona declarada, el campo se llama
  `generated_at_utc` (UTC explícito) en vez de `generated_at`, y
  `metadata.timezone` queda en `null`; se agrega una nota en
  `limitations` explicando la sustitución.

Probado con el feed real (`test_timezone_offset_matches_declared_zone`,
compara el offset real contra `zoneinfo.ZoneInfo("America/Santiago")`
para esa fecha) y con un fixture sin `agency_timezone`
(`test_timezone_missing_falls_back_to_utc`).

---

## 9. Serialización JSON estricta

`to_native()` convierte recursivamente enteros/flotantes/booleanos de
NumPy, `pd.NA`, `NaN` y `Infinity` a tipos nativos o `None`.
`dumps_strict()` llama a `json.dumps(..., allow_nan=False)`: si pese a
la limpieza quedara algún `NaN`/`Infinity`/objeto no serializable,
`json.dumps` lanza `ValueError` en vez de producir un JSON que
técnicamente no es JSON estándar (`NaN` no lo es). Probado contra el
feed real (`test_json_output_is_strict_and_native`) y con un feed
sintético sin `route_short_name` para verificar que ese `None` no se
convierte en un `NaN` de pandas perdido en el camino
(`test_route_without_short_name_serializes_cleanly`).

---

## 10. Rendimiento

Con todas las correcciones (que añaden trabajo: identidades por
instancia de viaje en `area_analysis`, offset en cada bloque de
headway), el tiempo por consulta después de cargar el feed sube de
~0,11–0,15 s (v1.2) a ~0,3–0,4 s (v1.2.1) en el equipo de referencia,
todavía muy por debajo del objetivo de 2 s
(`test_query_performance_after_load`). La carga inicial del feed
(~10–14 s, dominada por parsear ~1,1 millones de filas de
`stop_times.txt`) sigue siendo un costo único por proceso, no por
consulta.

---

## 11. Alcance explícitamente fuera de esta versión

(sin cambios respecto de v1.2, reiterado por la sección 15 del
encargo v1.2.1)

- No se calcula un puntaje HABITA.
- No se usa ninguna API de enrutamiento externa para distancia
  caminable real.
- No se integran datos GTFS-Realtime.
- `exact_times=0` sigue siendo una estimación, nunca una observación
  real.
