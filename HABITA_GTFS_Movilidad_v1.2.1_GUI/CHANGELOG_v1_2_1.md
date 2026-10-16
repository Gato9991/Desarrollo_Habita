# CHANGELOG v1.2 → v1.2.1

Cada punto corresponde a la numeración de `ENCARGO_CLAUDE_GTFS_v1_2_1.md`.

## §3 — Corrección crítica 1: headway desplazado hasta la parada

- **Antes**: `_weighted_headway_for_group` (entonces sin ese nombre)
  intersectaba `fr["start_s"]`/`fr["end_s"]` de `frequencies.txt`
  directamente contra la ventana punta consultada, sin sumar
  `row.offset_s`. El error se detectó originalmente en un caso de
  control que luego fue retirado del proyecto (ver nota al final de
  este archivo); publicaba un headway ponderado que resultaba
  incoherente con la cantidad de salidas realmente generadas en esa
  misma ventana — una inconsistencia interna que ya delataba el error.
- **Ahora**: cada bloque se desplaza con
  `block_start_at_stop = fr["start_s"] + row.offset_s` (y análogo para
  `end_s`) antes de intersectarlo con la ventana. Resultado verificado
  contra el feed real con el control vigente (recorrido 511, sentido 1,
  en `PD561`, Estadio Nacional): `11,51 min` sin offset (incorrecto)
  vs. `10,96 min` con offset (corregido), punta tarde — ver el detalle
  fila por fila en `GTFS_ANALYZER_GUIDE_v1_2_1.md §1`.
- Se agregó `distinct_trip_id_patterns` y `concurrent_patterns_detected`
  para documentar explícitamente cuándo el promedio ponderado no
  equivale a la frecuencia combinada de servicios simultáneos (no
  ocurre en el recorrido 511 de `PD561` en la ventana tarde, donde los
  bloques relevantes son secuenciales; sí ocurre, por ejemplo, en el
  recorrido 508 de esa misma parada en la ventana mañana).
- Se separaron tres magnitudes con nombres distintos:
  `scheduled_departures`, `weighted_block_headway_min`,
  `effective_interval_from_generated_departures_min`. Ya no se llaman
  todas `headway_min`.
- Pruebas: `test_headway_with_offset_crossing_block_boundary` (fixture
  sintético con dos bloques consecutivos de headway distinto y un
  offset que desplaza la ventana consultada hasta cruzar el límite
  entre ambos; el resultado se contrasta contra una implementación
  independiente sin offset, que debe dar un valor distinto),
  `test_route_511_headway_with_offset_matches_expected` (control real
  sobre el Estadio Nacional, con aserción explícita de que el valor
  sin offset ya NO es el que se reporta).

## §4 — Corrección crítica 2: duplicación de viajes en el análisis por radio

- **Antes**: `area_analysis` sumaba una salida por cada fila de
  `stop_times` dentro del radio, bajo el nombre `total_departures`,
  mezclando "eventos de paso" con "viajes".
- **Ahora**: cada salida generada lleva una identidad reproducible
  (`(trip_id, frequency_block_index, departure_instance_index)` para
  viajes con `frequencies.txt`; `(trip_id, "scheduled")` para horario
  absoluto). Se reportan por separado `stop_departure_events` (cuenta
  cada evento, puede repetir un vehículo) y `unique_trip_instances`
  (cada instancia una sola vez), ambas con desglose `by_route`.
- Verificado contra los 4 controles cuantificados del encargo:

  | Ubicación | Ventana | Eventos | Únicas |
  |---|---|---:|---:|
  | Plaza de Armas | 07:00–09:00 | 3.077 | 554 |
  | Plaza de Armas | 17:00–19:00 | 2.772 | 489 |
  | Estadio Nacional | 07:00–09:00 | 1.802 | 517 |
  | Estadio Nacional | 17:00–19:00 | 1.835 | 527 |

  (`TestControlesTerritorialesHABITA::test_controles_eventos_e_instancias`).
- Pruebas adicionales con fixtures mínimos, deliberadamente simples,
  para aislar el mecanismo de deduplicación:
  `test_single_trip_crossing_three_stops_counts_one_unique_instance`
  (1 viaje × 3 paradas del radio = 3 eventos, 1 instancia única) y
  `test_events_vs_unique_instances_differ_with_multiple_trips` (2
  viajes × 2 paradas = 4 eventos, 2 instancias únicas).
- `limitations` documenta explícitamente que ninguna de las dos
  magnitudes debe leerse como "cantidad de buses" sin esta aclaración.

## §5 — Rendimiento y coherencia documental (Haversine)

- **Antes**: `analyze()` llamaba a `closest_stop_analysis` y a
  `area_analysis` (500 m y 800 m) por separado, y cada una ejecutaba de
  nuevo `compute_distances()` — 3 cálculos de Haversine por consulta,
  pese a que la documentación afirmaba que se calculaba una sola vez.
- **Ahora**: `analyze()` calcula `distances = self.compute_distances(lat, lon)`
  una sola vez y la pasa explícitamente a `nearest_physical_stop`,
  `nearest_active_stop_position`, `_stop_detail` y a las dos llamadas
  de `area_analysis`.
- Prueba: `test_compute_distances_called_once_per_analyze`, con
  `unittest.mock.patch` envolviendo el método real y contando
  invocaciones (`call_count == 1`).

## §6 — Compatibilidad GTFS

### 6.1 `frequencies.txt` opcional

- Se sacó de `REQUIRED_FILES`. Si falta, se crea un DataFrame vacío con
  las columnas esperadas y `_freq_by_trip = {}`.
- Prueba: `test_feed_without_frequencies_file`.

### 6.2 Calendario flexible

- `calendar.txt` y `calendar_dates.txt` ya no exigen ambos a la vez:
  se acepta solo `calendar.txt`, ambos, o solo `calendar_dates.txt`.
  Si ninguno existe, `GTFSDataError` explícito.
- Pruebas: `test_feed_with_both_calendar_files`,
  `test_feed_with_only_calendar_dates`,
  `test_feed_without_any_calendar_file_raises`.

## §7 — Parada física más cercana vs. parada activa más cercana

- **Antes**: se elegía la parada geográficamente más cercana y recién
  después se comprobaba si tenía viajes activos; si no los tenía, el
  programa devolvía "sin servicio" aunque existiera otra parada activa
  ligeramente más lejos.
- **Ahora**: `nearest_physical_stop` y `nearest_active_stop` son
  cálculos explícitos e independientes; `same_as_nearest_physical_stop`
  indica si coinciden. Los controles espaciales de referencia se
  conservan: para Plaza de Armas y Estadio Nacional, en la fecha de
  control, ambas siguen siendo `PG1599` y `PD561` respectivamente.
- Prueba: `test_nearest_physical_inactive_second_stop_active` (parada A
  física más cercana pero sin servicio entre semana; parada B más
  lejana pero con servicio activo).

## §8 — `pickup_type`, `location_type` y accesibilidad real

- Se excluyen de toda cuenta de salidas (ventana de servicio, headway,
  `area_analysis`) las filas de `stop_times.txt` con `pickup_type=="1"`.
  `pickup_type` vacío o `"0"` se trata como abordaje permitido; otros
  valores GTFS se documentan si aparecen (`self._other_pickup_types`).
- Se agregó una máscara de "parada abordable" (`_boardable_mask`):
  `location_type` en `{"", "0"}` **y** presencia real en
  `stop_times.txt` (5.990 filas de `stops.txt` del feed real no
  cumplen esto último: mayormente entradas y nodos de estación). Se
  usa tanto para `nearest_physical_stop` como para
  `nearest_active_stop`.
- En los dos controles actuales, ninguna parada dentro de 800 m tiene
  `pickup_type=1` ni `location_type` distinto de vacío, así que estos
  cambios no alteran los resultados ya validados — confirmado
  explícitamente antes de implementar, revisando el feed real.
- Pruebas: `test_pickup_type_1_excluded_from_departures`,
  `test_non_boardable_location_type_ignored`.

## §9 — Zona horaria y trazabilidad

- **Antes**: `metadata.timezone` se fijaba en `"America/Santiago"` como
  literal, mientras `generated_at` usaba `datetime.now()` sin zona
  (dependiente de la zona del sistema/contenedor, potencialmente UTC).
- **Ahora**: la zona horaria se deriva de `agency_timezone` en
  `agency.txt` vía `zoneinfo.ZoneInfo`. Si hay más de una zona
  declarada, se documenta la ambigüedad en `limitations`. Si no hay
  ninguna, el campo se renombra a `generated_at_utc` en vez de mentir
  sobre la zona.
- Pruebas: `test_timezone_offset_matches_declared_zone` (compara el
  offset real de `generated_at` contra `zoneinfo` para esa fecha exacta,
  detectando incluso errores de horario de verano),
  `test_timezone_missing_falls_back_to_utc`.

## §10 — Serialización y casos borde

- `to_native()` + `dumps_strict()` (con `allow_nan=False`) en vez de
  `json.dumps` simple: cualquier `NaN`/`Infinity` remanente ahora
  produce un error explícito en vez de un JSON no estándar.
- `headway_secs <= 0` en `frequencies.txt` ahora lanza `GTFSDataError`
  explícito al cargar el feed, en vez de continuar silenciosamente
  (antes: `if hw <= 0: continue`).
- Un bloque de frecuencia con `start_time == end_time` genera
  correctamente **cero** salidas (no un error ni un `min([])`).
- `--out` inexistente se crea automáticamente (`os.makedirs(...,
  exist_ok=True)`) en vez de fallar con `FileNotFoundError`.
- ZIP con los `.txt` dentro de una subcarpeta: ya funcionaba en v1.2
  (por usar `os.path.basename` al indexar el ZIP) y ahora tiene una
  prueba explícita que lo confirma (`test_zip_with_files_in_subfolder`).
- `route_short_name` ausente como columna completa (no solo `NaN` por
  fila) ya no provoca `KeyError` en `area_analysis` — bug real
  encontrado al escribir `test_route_without_short_name_serializes_cleanly`,
  corregido comprobando la existencia de la columna antes de indexar.
- Pruebas: `test_invalid_headway_raises_explicit_error`,
  `test_frequency_block_that_generates_no_departures`,
  `test_out_dir_is_created_if_missing`,
  `test_json_output_is_strict_and_native`.

## Bug adicional encontrado al probar (no listado explícitamente en el encargo)

Al escribir el fixture con una ruta sin columna `route_short_name`
(reutilizando el caso ya cubierto para otros campos en v1.2),
`area_analysis()` fallaba con `KeyError: 'route_short_name'` porque
`self.routes.set_index("route_id")["route_short_name"]` asume que la
columna existe. v1.2 ya toleraba esto en `_stop_detail`
(`route_short_name` ausente por fila), pero no en `area_analysis`
(columna ausente por completo). Corregido verificando
`"route_short_name" in self.routes.columns` antes de indexar, con
fallback al propio `route_id`.

## Controles conservados (recalculados sobre el territorio HABITA vigente)

| Ubicación | Parada más cercana | Distancia | Paradas 500 m | Paradas 800 m | Rutas activas 800 m, miércoles |
|---|---|---:|---:|---:|---:|
| Plaza de Armas - San Bernardo | `PG1599` | 94,2 m | 23 | 57 | 22 |
| Estadio Nacional Julio Martínez Prádanos | `PD561` | 308,0 m | 25 | 46 | 19 |

Verificados en `TestControlesTerritorialesHABITA::test_controles_espaciales`.
Un tercer caso de control usado durante el desarrollo de v1.2.1 fue
retirado de este proyecto en una limpieza posterior; los defectos que
ese caso ayudó a detectar (§3 y §4 de este changelog) siguen
documentados y probados con los dos casos vigentes.

## Resultado real de pytest (última ejecución)

```
GTFS_TEST_PATH=./GTFS_20260829 python -m pytest test_gtfs_analyzer_v1_2_1.py -v
...
38 passed in 91.9s
```

38 aprobadas, 0 fallidas, 0 omitidas, 0 advertencias. Todas las pruebas
de integración contra el feed real (`V167.20260829`) se ejecutaron
efectivamente (no quedaron como `SKIPPED`), porque `GTFS_TEST_PATH`
apuntó al feed real durante esta ejecución.
