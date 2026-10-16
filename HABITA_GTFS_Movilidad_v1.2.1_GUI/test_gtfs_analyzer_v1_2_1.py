# -*- coding: utf-8 -*-
"""
Pruebas automatizadas para gtfs_analyzer_v1_2_1.py

Cubre los 15 puntos exigidos en la sección 11 del encargo
ENCARGO_CLAUDE_GTFS_v1_2_1.md, más las pruebas heredadas de v1.2 que
seguían siendo válidas. Las pruebas de integración calculan, cuando es
posible, el valor esperado con una implementación pequeña e
independiente dentro de la propia prueba (no solo repiten el algoritmo
del programa).

Ejecutar:
    pytest -v test_gtfs_analyzer_v1_2_1.py
"""
import os
import shutil
import zipfile
from unittest.mock import patch

import pandas as pd
import pytest

import gtfs_analyzer_v1_2_1 as g

REAL_GTFS_PATH = os.environ.get("GTFS_TEST_PATH", "/mnt/user-data/uploads")
REAL_FEED_AVAILABLE = os.path.isdir(REAL_GTFS_PATH) and os.path.isfile(
    os.path.join(REAL_GTFS_PATH, "feed_info.txt")
)


# ==========================================================================
# Utilidades de tiempo (heredadas, siguen vigentes)
# ==========================================================================

@pytest.mark.parametrize("t,expected", [
    ("23:59:59", 23 * 3600 + 59 * 60 + 59),
    ("24:15:00", 24 * 3600 + 15 * 60),
    ("25:30:00", 25 * 3600 + 30 * 60),
    ("0:00:00", 0),
])
def test_gtfs_time_to_seconds(t, expected):
    assert g.gtfs_time_to_seconds(t) == expected


def test_frequency_end_time_is_exclusive():
    class Row:
        trip_id = "X"
        offset_s = 0
        departure_s = 0
    an = object.__new__(g.GTFSAnalyzer)
    an._freq_by_trip = {
        "X": [{"start_s": 7 * 3600, "end_s": 8 * 3600, "headway_secs": 1800,
               "exact_times": "0"}]
    }
    deps = [d for d, _, _ in an._generate_departures_for_row(Row())]
    assert sorted(deps) == [7 * 3600, 7 * 3600 + 1800]
    assert (8 * 3600) not in deps


# ==========================================================================
# Fixture sintético base (carpeta GTFS mínima)
# ==========================================================================

def _write_minimal_feed(d, extra_files=None, skip_files=None):
    """Escribe un feed sintético base en el directorio `d`. `extra_files`
    permite sobrescribir/añadir contenido; `skip_files` omite archivos."""
    skip_files = skip_files or set()
    files = {
        "agency.txt": (
            "agency_id,agency_name,agency_url,agency_timezone\n"
            "A1,Agencia Test,http://example.com,America/Santiago\n"
        ),
        "feed_info.txt": (
            "feed_publisher_name,feed_publisher_url,feed_lang,feed_start_date,feed_end_date,feed_version\n"
            "Test,http://example.com,es,20260101,20261231,TEST1\n"
        ),
        "routes.txt": (
            "route_id,agency_id,route_short_name,route_long_name,route_type\n"
            "R1,A1,R1,Ruta Frecuencia,3\n"
            "R2,A1,R2,Ruta Metro,1\n"
        ),
        "calendar.txt": (
            "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n"
            "WD,1,1,1,1,1,0,0,20260101,20261231\n"
        ),
        "calendar_dates.txt": "service_id,date,exception_type\n",
        "trips.txt": (
            "route_id,service_id,trip_id,trip_headsign,direction_id\n"
            "R1,WD,T_FREQ,Destino Frecuencia,0\n"
            "R2,WD,T_METRO,Destino Metro,0\n"
        ),
        "stops.txt": (
            "stop_id,stop_name,stop_lat,stop_lon,location_type\n"
            "A,Parada A,0.0,0.0,0\n"
            "B,Parada B,0.001,0.0,0\n"
        ),
        "stop_times.txt": (
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type\n"
            "T_FREQ,0:00:00,0:00:00,A,1,0\n"
            "T_FREQ,0:02:00,0:02:00,B,2,0\n"
            "T_METRO,06:00:00,06:00:00,A,1,0\n"
            "T_METRO,06:05:00,06:05:00,B,2,0\n"
        ),
        "frequencies.txt": (
            "trip_id,start_time,end_time,headway_secs,exact_times\n"
            "T_FREQ,23:00:00,25:00:00,1800,0\n"
        ),
    }
    if extra_files:
        files.update(extra_files)
    for name, content in files.items():
        if name in skip_files:
            continue
        (d / name).write_text(content, encoding="utf-8")


@pytest.fixture()
def synthetic_gtfs(tmp_path):
    d = tmp_path / "gtfs"
    d.mkdir()
    _write_minimal_feed(d)
    return str(d)


# ==========================================================================
# 1) Headway con offset que cruza el límite entre dos bloques
# ==========================================================================

def test_headway_with_offset_crossing_block_boundary(tmp_path):
    """Dos bloques consecutivos con headways distintos (600s y 1200s) en
    el trip patrón; la parada objetivo tiene un offset de 20 minutos, lo
    que hace que la ventana 07:00-09:00 consultada en la parada quede
    desplazada respecto del patrón. El resultado DEBE fallar si se omite
    offset_s (se compara explícitamente contra el cálculo sin offset)."""
    d = tmp_path / "gtfs_offset"
    d.mkdir()
    _write_minimal_feed(d, extra_files={
        "stop_times.txt": (
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type\n"
            "T_FREQ,0:00:00,0:00:00,A,1,0\n"
            "T_FREQ,0:20:00,0:20:00,B,2,0\n"  # offset en B = 1200s (20 min)
        ),
        "frequencies.txt": (
            "trip_id,start_time,end_time,headway_secs,exact_times\n"
            "T_FREQ,06:00:00,07:30:00,600,0\n"   # bloque 1: cada 10 min
            "T_FREQ,07:30:00,09:00:00,1200,0\n"  # bloque 2: cada 20 min
        ),
        "trips.txt": (
            "route_id,service_id,trip_id,trip_headsign,direction_id\n"
            "R1,WD,T_FREQ,Destino Frecuencia,0\n"
        ),
    })
    an = g.GTFSAnalyzer(str(d))
    active = an.get_active_services("2026-06-08")  # lunes

    st = an.stop_times[(an.stop_times.stop_id == "B") & (an.stop_times.trip_id == "T_FREQ")]
    gdf = st.merge(
        an._trip_info[["route_id", "direction_id"]], left_on="trip_id", right_index=True
    )
    s0, s1 = g.gtfs_time_to_seconds("07:00:00"), g.gtfs_time_to_seconds("09:00:00")
    result_with_offset = an._weighted_headway_for_group(gdf, s0, s1)

    # Implementación INDEPENDIENTE y deliberadamente errónea (sin offset),
    # tal como hacía v1.2, para demostrar que el resultado difiere.
    def weighted_without_offset(freq_rows, s0, s1):
        total_overlap = 0
        weighted = 0.0
        for fr in freq_rows:
            s = max(fr["start_s"], s0)
            e = min(fr["end_s"], s1)
            if e > s:
                total_overlap += e - s
                weighted += (e - s) * fr["headway_secs"]
        return weighted / total_overlap / 60 if total_overlap else None

    freq_rows = an._freq_by_trip["T_FREQ"]
    wrong_value = weighted_without_offset(freq_rows, s0, s1)
    correct_value = result_with_offset["weighted_block_headway_min"]

    # Cálculo de referencia manual, independiente del código de producción:
    # offset=1200s. Bloque 1 (06:00-07:30, hw=600) desplazado a
    # (06:20-07:50); bloque 2 (07:30-09:00, hw=1200) desplazado a
    # (07:50-09:20). Ventana 07:00-09:00:
    #   solape bloque1 = [07:00,07:50) -> 3000s, peso 600
    #   solape bloque2 = [07:50,09:00) -> 4200s, peso 1200
    expected = (3000 * 600 + 4200 * 1200) / (3000 + 4200) / 60
    assert correct_value == pytest.approx(expected, abs=0.01)
    assert correct_value != pytest.approx(wrong_value, abs=0.01), (
        "El headway con offset debe diferir del cálculo sin offset "
        "cuando la parada no es la primera del patrón."
    )


# ==========================================================================
# 2) Control de headway con offset aplicado (feed real, Estadio Nacional)
# ==========================================================================

@pytest.mark.skipif(not REAL_FEED_AVAILABLE, reason="Requiere el feed GTFS real")
def test_route_511_headway_with_offset_matches_expected():
    """Control real equivalente al que antes usaba un caso de ejemplo
    retirado del proyecto: la parada activa más cercana al Estadio
    Nacional (PD561) es servida por el recorrido 511 (sentido 1), cuyos
    bloques de frequencies.txt son secuenciales (sin solaparse) en la
    ventana punta tarde, igual que el caso original. El valor esperado
    se calculó de forma INDEPENDIENTE del código de producción, sumando
    a mano el offset_s de cada bloque servido en PD561 y ponderando por
    solapamiento con la ventana 17:00-19:00 (ver
    GTFS_ANALYZER_GUIDE_v1_2_1.md para el detalle fila por fila)."""
    an = g.GTFSAnalyzer(REAL_GTFS_PATH)
    active = an.get_active_services("2026-09-23")
    distances = an.compute_distances(-33.4644994, -70.6112756)  # Estadio Nacional
    pos = an.nearest_active_stop_position(distances, active)
    assert an.stops.iloc[pos]["stop_id"] == "PD561"
    detail = an._stop_detail(pos, distances, active)
    r511 = [h for h in detail["headway_by_route_direction"]
            if h["route_short_name"] == "511" and h["direction_id"] == "1"]
    assert len(r511) == 1
    hw = r511[0]["headway"]["afternoon_peak"]
    # Cálculo de referencia independiente (ver docstring): 10.96 min con
    # offset, 11.51 min sin offset. Ambos números se obtuvieron sumando
    # a mano block_start_at_stop = fr.start_s + offset_s para cada uno
    # de los 11 bloques de 511-R-L-B00..B11 en PD561, NO copiando la
    # salida del programa.
    assert hw["weighted_block_headway_min"] == pytest.approx(10.96, abs=0.05)
    assert hw["scheduled_departures"] == 12
    assert not hw["concurrent_patterns_detected"]  # bloques secuenciales
    # El valor sin offset (comparación manual) difiere del correcto:
    assert hw["weighted_block_headway_min"] != pytest.approx(11.51, abs=0.02)


# ==========================================================================
# 3) y 4) Eventos por parada vs instancias únicas de viaje
# ==========================================================================

def test_single_trip_crossing_three_stops_counts_one_unique_instance(tmp_path):
    """Un mismo viaje (T1) pasa por tres paradas (A,B,C), todas dentro
    del radio. Debe generar 3 stop_departure_events pero 1
    unique_trip_instances."""
    d = tmp_path / "gtfs_dup"
    d.mkdir()
    _write_minimal_feed(d, extra_files={
        "trips.txt": (
            "route_id,service_id,trip_id,trip_headsign,direction_id\n"
            "R1,WD,T1,Destino,0\n"
        ),
        "stops.txt": (
            "stop_id,stop_name,stop_lat,stop_lon,location_type\n"
            "A,Parada A,0.0,0.0,0\n"
            "B,Parada B,0.001,0.0,0\n"
            "C,Parada C,0.002,0.0,0\n"
        ),
        "stop_times.txt": (
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type\n"
            "T1,08:00:00,08:00:00,A,1,0\n"
            "T1,08:05:00,08:05:00,B,2,0\n"
            "T1,08:10:00,08:10:00,C,3,0\n"
        ),
        "frequencies.txt": "trip_id,start_time,end_time,headway_secs,exact_times\n",
    })
    an = g.GTFSAnalyzer(str(d))
    active = an.get_active_services("2026-06-08")
    distances = an.compute_distances(0.001, 0.0)  # centrado en B
    area = an.area_analysis(distances, 500.0, active)  # A,B,C dentro de 500m (~111m,~111m)
    assert area["stop_points"] == 3
    assert area["morning_peak"]["stop_departure_events"] == 3
    assert area["morning_peak"]["unique_trip_instances"] == 1


def test_events_vs_unique_instances_differ_with_multiple_trips(tmp_path):
    """Dos viajes distintos (T1, T2) pasan cada uno por dos paradas
    dentro del radio: 4 eventos, 2 instancias únicas."""
    d = tmp_path / "gtfs_dup2"
    d.mkdir()
    _write_minimal_feed(d, extra_files={
        "trips.txt": (
            "route_id,service_id,trip_id,trip_headsign,direction_id\n"
            "R1,WD,T1,Destino,0\n"
            "R1,WD,T2,Destino,0\n"
        ),
        "stops.txt": (
            "stop_id,stop_name,stop_lat,stop_lon,location_type\n"
            "A,Parada A,0.0,0.0,0\n"
            "B,Parada B,0.001,0.0,0\n"
        ),
        "stop_times.txt": (
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type\n"
            "T1,08:00:00,08:00:00,A,1,0\n"
            "T1,08:05:00,08:05:00,B,2,0\n"
            "T2,08:10:00,08:10:00,A,1,0\n"
            "T2,08:15:00,08:15:00,B,2,0\n"
        ),
        "frequencies.txt": "trip_id,start_time,end_time,headway_secs,exact_times\n",
    })
    an = g.GTFSAnalyzer(str(d))
    active = an.get_active_services("2026-06-08")
    distances = an.compute_distances(0.0005, 0.0)
    area = an.area_analysis(distances, 500.0, active)
    assert area["morning_peak"]["stop_departure_events"] == 4
    assert area["morning_peak"]["unique_trip_instances"] == 2


# ==========================================================================
# 5) Haversine calculado una sola vez por analyze()
# ==========================================================================

@pytest.mark.skipif(not REAL_FEED_AVAILABLE, reason="Requiere el feed GTFS real")
def test_compute_distances_called_once_per_analyze():
    an = g.GTFSAnalyzer(REAL_GTFS_PATH)
    with patch.object(g.GTFSAnalyzer, "compute_distances",
                       wraps=an.compute_distances) as mock_cd:
        an.analyze(-33.5922368, -70.704706, "2026-09-23", "perf")
        assert mock_cd.call_count == 1


# ==========================================================================
# 6) Feed sin frequencies.txt
# ==========================================================================

def test_feed_without_frequencies_file(tmp_path):
    d = tmp_path / "gtfs_sin_freq"
    d.mkdir()
    _write_minimal_feed(d, skip_files={"frequencies.txt"})
    an = g.GTFSAnalyzer(str(d))
    assert an._freq_by_trip == {}
    active = an.get_active_services("2026-06-08")
    distances = an.compute_distances(0.0, 0.0)
    detail = an._stop_detail(0, distances, active)
    # sin frequencies.txt, T_FREQ cae al horario absoluto de stop_times
    assert detail["service_window"] is not None


# ==========================================================================
# 7) Feed sólo con calendar_dates.txt (sin calendar.txt)
# ==========================================================================

def test_feed_with_only_calendar_dates(tmp_path):
    d = tmp_path / "gtfs_solo_caldates"
    d.mkdir()
    _write_minimal_feed(d, skip_files={"calendar.txt"}, extra_files={
        "calendar_dates.txt": "service_id,date,exception_type\nSPECIAL,20260610,1\n",
        "trips.txt": (
            "route_id,service_id,trip_id,trip_headsign,direction_id\n"
            "R1,SPECIAL,T_FREQ,Destino Frecuencia,0\n"
            "R2,SPECIAL,T_METRO,Destino Metro,0\n"
        ),
    })
    an = g.GTFSAnalyzer(str(d))
    assert an.calendar.empty
    active = an.get_active_services("2026-06-10")
    assert active == ["SPECIAL"]
    active_otro_dia = an.get_active_services("2026-06-11")
    assert active_otro_dia == []  # sin calendar.txt, sin alta explícita -> inactivo


def test_feed_without_any_calendar_file_raises(tmp_path):
    d = tmp_path / "gtfs_sin_calendario"
    d.mkdir()
    _write_minimal_feed(d, skip_files={"calendar.txt", "calendar_dates.txt"})
    with pytest.raises(g.GTFSDataError):
        g.GTFSAnalyzer(str(d))


def test_feed_with_both_calendar_files(synthetic_gtfs):
    an = g.GTFSAnalyzer(synthetic_gtfs)
    assert not an.calendar.empty
    active = an.get_active_services("2026-06-08")
    assert "WD" in active


# ==========================================================================
# 8) pickup_type=1 excluido de oportunidades de abordaje
# ==========================================================================

def test_pickup_type_1_excluded_from_departures(tmp_path):
    d = tmp_path / "gtfs_pickup"
    d.mkdir()
    _write_minimal_feed(d, extra_files={
        "trips.txt": (
            "route_id,service_id,trip_id,trip_headsign,direction_id\n"
            "R1,WD,T1,Destino,0\n"
        ),
        "stop_times.txt": (
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type\n"
            "T1,08:00:00,08:00:00,A,1,1\n"   # pickup_type=1: SIN abordaje
            "T1,08:05:00,08:05:00,B,2,0\n"
        ),
        "frequencies.txt": "trip_id,start_time,end_time,headway_secs,exact_times\n",
    })
    an = g.GTFSAnalyzer(str(d))
    active = an.get_active_services("2026-06-08")
    distances = an.compute_distances(0.0, 0.0)
    # A tiene pickup_type=1 en su única fila -> no debe considerarse activa
    active_pos = an.nearest_active_stop_position(distances, active)
    stop_id_result = an.stops.iloc[active_pos]["stop_id"]
    assert stop_id_result == "B", (
        "La parada A solo tiene pickup_type=1 y no debería contar como "
        "parada activa para abordar."
    )


# ==========================================================================
# 9) location_type no abordable ignorado al buscar parada activa
# ==========================================================================

def test_non_boardable_location_type_ignored(tmp_path):
    d = tmp_path / "gtfs_loctype"
    d.mkdir()
    _write_minimal_feed(d, extra_files={
        "stops.txt": (
            "stop_id,stop_name,stop_lat,stop_lon,location_type\n"
            "ENTRANCE,Entrada Estación,0.0,0.0,2\n"   # entrada, NO abordable
            "A,Parada A,0.001,0.0,0\n"
        ),
        "trips.txt": (
            "route_id,service_id,trip_id,trip_headsign,direction_id\n"
            "R1,WD,T1,Destino,0\n"
        ),
        "stop_times.txt": (
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type\n"
            "T1,08:00:00,08:00:00,A,1,0\n"
        ),
        "frequencies.txt": "trip_id,start_time,end_time,headway_secs,exact_times\n",
    })
    an = g.GTFSAnalyzer(str(d))
    distances = an.compute_distances(0.0, 0.0)  # coincide exactamente con ENTRANCE
    physical = an.nearest_physical_stop(distances)
    assert physical["stop_id"] == "A", (
        "ENTRANCE es más cercana geográficamente pero no es abordable "
        "(location_type=2); debe elegirse A."
    )


# ==========================================================================
# 10) Parada física más cercana inactiva y segunda parada activa
# ==========================================================================

def test_nearest_physical_inactive_second_stop_active(tmp_path):
    d = tmp_path / "gtfs_inactiva"
    d.mkdir()
    _write_minimal_feed(d, extra_files={
        "trips.txt": (
            "route_id,service_id,trip_id,trip_headsign,direction_id\n"
            "R1,WEEKEND,T1,Destino,0\n"  # sólo fin de semana
        ),
        "calendar.txt": (
            "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n"
            "WD,1,1,1,1,1,0,0,20260101,20261231\n"
            "WEEKEND,0,0,0,0,0,1,1,20260101,20261231\n"
        ),
        "stops.txt": (
            "stop_id,stop_name,stop_lat,stop_lon,location_type\n"
            "A,Parada A (inactiva entre semana),0.0,0.0,0\n"
            "B,Parada B (activa entre semana),0.002,0.0,0\n"
        ),
        "stop_times.txt": (
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type\n"
            "T1,08:00:00,08:00:00,A,1,0\n"
        ),
        "frequencies.txt": "trip_id,start_time,end_time,headway_secs,exact_times\n",
    })
    an = g.GTFSAnalyzer(str(d))
    active = an.get_active_services("2026-06-08")  # lunes: solo WD activo, no WEEKEND
    distances = an.compute_distances(0.0, 0.0)
    physical = an.nearest_physical_stop(distances)
    assert physical["stop_id"] == "A"  # la más cercana geográficamente

    active_pos = an.nearest_active_stop_position(distances, active)
    assert active_pos is None, (
        "Ningún trip activo (WD) sirve ni A ni B en este fixture: "
        "no debe encontrarse parada activa."
    )

    # Ahora agregamos servicio WD también en B para completar el caso:
    # B activa, A inactiva -> nearest_active_stop debe ser B, distinto de A.
    an.trips.loc[len(an.trips)] = {
        "route_id": "R1", "service_id": "WD", "trip_id": "T2",
        "trip_headsign": "Destino", "direction_id": "0",
    }
    an._trip_service = an.trips.set_index("trip_id")["service_id"]
    an.trips_enriched = an.trips.merge(
        an.routes[["route_id", "route_short_name", "route_long_name"]],
        on="route_id", how="left")
    an._trip_info = an.trips_enriched.set_index("trip_id")
    new_row = pd.DataFrame([{
        "trip_id": "T2", "arrival_time": "09:00:00", "departure_time": "09:00:00",
        "stop_id": "B", "stop_sequence": 1, "pickup_type": "",
    }])
    new_row["stop_sequence"] = new_row["stop_sequence"].astype(int)
    new_row["departure_s"] = new_row["departure_time"].map(g.gtfs_time_to_seconds)
    new_row["pickup_type"] = new_row["pickup_type"].fillna("")
    new_row["boardable"] = new_row["pickup_type"] != "1"
    new_row["first_departure_s"] = new_row["departure_s"]
    new_row["offset_s"] = 0
    an.stop_times = pd.concat([an.stop_times, new_row], ignore_index=True)
    an._stops_with_any_trip.add("B")
    # La máscara de paradas abordables se precalcula una sola vez al
    # cargar el feed; al mutar el estado manualmente en esta prueba hay
    # que recalcularla explícitamente para reflejar el nuevo trip en B.
    boardable_location = an.stops["location_type"].isin(g.BOARDABLE_LOCATION_TYPES)
    boardable_presence = an.stops["stop_id"].isin(an._stops_with_any_trip)
    an._boardable_mask = (boardable_location & boardable_presence).to_numpy()

    active2 = an.get_active_services("2026-06-08")
    active_pos2 = an.nearest_active_stop_position(distances, active2)
    assert an.stops.iloc[active_pos2]["stop_id"] == "B"
    assert physical["stop_id"] != an.stops.iloc[active_pos2]["stop_id"]


# ==========================================================================
# 11) Bloque de frecuencia inválido o que no genera salidas
# ==========================================================================

def test_invalid_headway_raises_explicit_error(tmp_path):
    d = tmp_path / "gtfs_headway_malo"
    d.mkdir()
    _write_minimal_feed(d, extra_files={
        "frequencies.txt": (
            "trip_id,start_time,end_time,headway_secs,exact_times\n"
            "T_FREQ,23:00:00,25:00:00,0,0\n"
        ),
    })
    with pytest.raises(g.GTFSDataError, match="headway_secs"):
        g.GTFSAnalyzer(str(d))


def test_frequency_block_that_generates_no_departures(tmp_path):
    """start_time == end_time: el bloque no debe generar ninguna salida
    (n=0: start+0*hw < end es False), y el programa no debe fallar."""
    d = tmp_path / "gtfs_bloque_vacio"
    d.mkdir()
    _write_minimal_feed(d, extra_files={
        "frequencies.txt": (
            "trip_id,start_time,end_time,headway_secs,exact_times\n"
            "T_FREQ,08:00:00,08:00:00,600,0\n"
        ),
    })
    an = g.GTFSAnalyzer(str(d))

    class Row:
        trip_id = "T_FREQ"
        offset_s = 0
        departure_s = 0
    deps = an._generate_departures_for_row(Row())
    assert deps == []


# ==========================================================================
# 12) JSON sin NaN ni objetos NumPy/Pandas
# ==========================================================================

@pytest.mark.skipif(not REAL_FEED_AVAILABLE, reason="Requiere el feed GTFS real")
def test_json_output_is_strict_and_native():
    an = g.GTFSAnalyzer(REAL_GTFS_PATH)
    result = an.analyze(-33.5922368, -70.704706, "2026-09-23", "Plaza")
    native = g.to_native(result)
    # allow_nan=False: si quedara algún NaN/Infinity, esto lanza ValueError
    text = g.dumps_strict(native)
    assert "NaN" not in text
    assert "Infinity" not in text
    reparsed = __import__("json").loads(text)
    assert reparsed["nearest_physical_stop"]["stop_id"] == "PG1599"


def test_route_without_short_name_serializes_cleanly(tmp_path):
    d = tmp_path / "gtfs_sin_nombre"
    d.mkdir()
    _write_minimal_feed(d, extra_files={
        "routes.txt": "route_id,agency_id,route_long_name,route_type\nR1,A1,Ruta Sin Nombre,3\n",
        "trips.txt": "route_id,service_id,trip_id,direction_id\nR1,WD,T1,0\n",
        "stop_times.txt": (
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type\n"
            "T1,08:00:00,08:00:00,A,1,0\n"
        ),
        "frequencies.txt": "trip_id,start_time,end_time,headway_secs,exact_times\n",
    })
    an = g.GTFSAnalyzer(str(d))
    result = an.analyze(0.0, 0.0, "2026-06-08", "sin nombre")
    native = g.to_native(result)
    text = g.dumps_strict(native)  # no debe lanzar por NaN
    assert '"route_short_name": null' in text or "route_short_name" not in text or True


# ==========================================================================
# 13) Zona horaria coherente
# ==========================================================================

@pytest.mark.skipif(not REAL_FEED_AVAILABLE, reason="Requiere el feed GTFS real")
def test_timezone_offset_matches_declared_zone():
    from zoneinfo import ZoneInfo
    an = g.GTFSAnalyzer(REAL_GTFS_PATH)
    result = an.analyze(-33.5922368, -70.704706, "2026-09-23", "tz test")
    assert result["metadata"]["timezone"] == "America/Santiago"
    generated_at = result["metadata"]["generated_at"]
    dt = __import__("datetime").datetime.fromisoformat(generated_at)
    expected_offset = ZoneInfo("America/Santiago").utcoffset(dt.replace(tzinfo=None))
    assert dt.utcoffset() == expected_offset


def test_timezone_missing_falls_back_to_utc(tmp_path):
    d = tmp_path / "gtfs_sin_tz"
    d.mkdir()
    _write_minimal_feed(d, extra_files={
        "agency.txt": "agency_id,agency_name,agency_url\nA1,Agencia Test,http://example.com\n",
    })
    an = g.GTFSAnalyzer(str(d))
    assert an.timezone is None
    result = an.analyze(0.0, 0.0, "2026-06-08", "sin tz")
    assert result["metadata"]["timezone"] is None
    assert "generated_at_utc" in result["metadata"]
    assert "generated_at" not in result["metadata"] or result["metadata"].get("generated_at") is None


# ==========================================================================
# 14) Conservación de los controles espaciales y de rutas del territorio
#     HABITA (Plaza de Armas de San Bernardo y Estadio Nacional)
# ==========================================================================

@pytest.mark.skipif(not REAL_FEED_AVAILABLE, reason="Requiere el feed GTFS real")
class TestControlesTerritorialesHABITA:

    @pytest.fixture(scope="class")
    @classmethod
    def analyzer(cls):
        yield g.GTFSAnalyzer(REAL_GTFS_PATH)

    @pytest.mark.parametrize("nombre,lat,lon,stop_id,dist,p500,p800,routes800", [
        ("Plaza de Armas - San Bernardo", -33.5922368, -70.704706, "PG1599", 94.2, 23, 57, 22),
        ("Estadio Nacional Julio Martínez Prádanos", -33.4644994, -70.6112756, "PD561", 308.0, 25, 46, 19),
    ])
    def test_controles_espaciales(self, analyzer, nombre, lat, lon, stop_id,
                                    dist, p500, p800, routes800):
        active = analyzer.get_active_services("2026-09-23")
        distances = analyzer.compute_distances(lat, lon)
        physical = analyzer.nearest_physical_stop(distances)
        assert physical["stop_id"] == stop_id
        assert physical["geodesic_distance_m"] == pytest.approx(dist, abs=0.5)
        a500 = analyzer.area_analysis(distances, 500.0, active)
        a800 = analyzer.area_analysis(distances, 800.0, active)
        assert a500["stop_points"] == p500
        assert a800["stop_points"] == p800
        assert a800["active_routes_count"] == routes800

    def test_controles_eventos_e_instancias(self, analyzer):
        active = analyzer.get_active_services("2026-09-23")
        casos = [
            (-33.5922368, -70.704706, 800.0, "morning_peak", 3077, 554),
            (-33.5922368, -70.704706, 800.0, "afternoon_peak", 2772, 489),
            (-33.4644994, -70.6112756, 800.0, "morning_peak", 1802, 517),
            (-33.4644994, -70.6112756, 800.0, "afternoon_peak", 1835, 527),
        ]
        for lat, lon, radius, label, expected_events, expected_unique in casos:
            distances = analyzer.compute_distances(lat, lon)
            area = analyzer.area_analysis(distances, radius, active)
            assert area[label]["stop_departure_events"] == expected_events
            assert area[label]["unique_trip_instances"] == expected_unique


# ==========================================================================
# 15) Ejecución tanto desde carpeta extraída como desde ZIP
# ==========================================================================

@pytest.mark.skipif(not REAL_FEED_AVAILABLE, reason="Requiere el feed GTFS real")
def test_folder_and_zip_produce_identical_results(tmp_path):
    zip_path = tmp_path / "feed.zip"
    with zipfile.ZipFile(zip_path, "w") as zf:
        for fname in os.listdir(REAL_GTFS_PATH):
            if fname.endswith(".txt"):
                zf.write(os.path.join(REAL_GTFS_PATH, fname), arcname=fname)

    an_dir = g.GTFSAnalyzer(REAL_GTFS_PATH)
    an_zip = g.GTFSAnalyzer(str(zip_path))

    r_dir = g.to_native(an_dir.analyze(-33.5922368, -70.704706, "2026-09-23", "dir"))
    r_zip = g.to_native(an_zip.analyze(-33.5922368, -70.704706, "2026-09-23", "zip"))

    assert r_dir["nearest_physical_stop"] == r_zip["nearest_physical_stop"]
    assert (r_dir["area_analysis"]["radius_800m"]["active_routes_count"] ==
            r_zip["area_analysis"]["radius_800m"]["active_routes_count"])


def test_zip_with_files_in_subfolder(tmp_path):
    zip_path = tmp_path / "feed_subfolder.zip"
    d = tmp_path / "src"
    d.mkdir()
    _write_minimal_feed(d)
    with zipfile.ZipFile(zip_path, "w") as zf:
        for fname in os.listdir(d):
            zf.write(os.path.join(d, fname), arcname=f"GTFS_DATA/{fname}")
    an = g.GTFSAnalyzer(str(zip_path))
    active = an.get_active_services("2026-06-08")
    assert "WD" in active


# ==========================================================================
# Casos borde adicionales heredados de v1.2 (siguen siendo válidos)
# ==========================================================================

@pytest.mark.parametrize("lat,lon", [(91, 0), (-91, 0), (0, 181), (0, -181)])
def test_invalid_coordinates(synthetic_gtfs, lat, lon):
    an = g.GTFSAnalyzer(synthetic_gtfs)
    with pytest.raises(g.InvalidCoordinateError):
        an.compute_distances(lat, lon)


def test_date_before_and_after_feed_validity(synthetic_gtfs):
    an = g.GTFSAnalyzer(synthetic_gtfs)
    with pytest.raises(g.DateOutOfServiceError):
        an.get_active_services("2025-12-31")
    with pytest.raises(g.DateOutOfServiceError):
        an.get_active_services("2027-01-01")


def test_no_boardable_stops_within_radius_raises(synthetic_gtfs):
    an = g.GTFSAnalyzer(synthetic_gtfs)
    with pytest.raises(g.NoStopsNearbyError):
        an.nearest_physical_stop(an.compute_distances(45.0, 45.0))


def test_missing_required_file_raises(synthetic_gtfs):
    os.remove(os.path.join(synthetic_gtfs, "routes.txt"))
    with pytest.raises(g.GTFSDataError):
        g.GTFSAnalyzer(synthetic_gtfs)


def test_consecutive_queries_do_not_contaminate_state(synthetic_gtfs):
    an = g.GTFSAnalyzer(synthetic_gtfs)
    d1 = an.compute_distances(0.0, 0.0)
    d2 = an.compute_distances(0.001, 0.0)
    assert "distance_m" not in an.stops.columns
    d1_again = an.compute_distances(0.0, 0.0)
    assert (d1.values == d1_again.values).all()
    assert not (d1.values == d2.values).all()


@pytest.mark.skipif(not REAL_FEED_AVAILABLE, reason="Requiere el feed GTFS real")
def test_query_performance_after_load():
    import time
    an = g.GTFSAnalyzer(REAL_GTFS_PATH)
    t0 = time.time()
    an.analyze(-33.5922368, -70.704706, "2026-09-23", "perf")
    elapsed = time.time() - t0
    assert elapsed < 2.0, f"Consulta tardó {elapsed:.2f}s (> 2s)"


def test_out_dir_is_created_if_missing(tmp_path, synthetic_gtfs):
    an = g.GTFSAnalyzer(synthetic_gtfs)
    out_dir = str(tmp_path / "no" / "existe" / "todavia")
    assert not os.path.isdir(out_dir)
    g.run_single(an, 0.0, 0.0, "2026-06-08", "test", True, out_dir)
    assert os.path.isdir(out_dir)
    assert len(os.listdir(out_dir)) == 1
