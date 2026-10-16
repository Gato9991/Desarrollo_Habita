#!/usr/bin/env python3
"""
GTFS Analyzer v1.2.1 - Análisis de accesibilidad a transporte público
Red Metropolitana de Movilidad, Santiago de Chile.

Corrige los defectos metodológicos confirmados de la v1.2 (ver
CHANGELOG_v1_2_1.md), sin perder ninguna de sus mejoras respecto a v1.1:

 - El headway ponderado por solapamiento ahora desplaza cada bloque de
   frequencies.txt hasta la parada consultada usando offset_s, en vez
   de comparar el bloque tal como aparece en el primer punto del patrón.
 - area_analysis distingue eventos de salida por parada
   (stop_departure_events, puede repetir un mismo vehículo si pasa por
   varias paradas del radio) de instancias únicas de viaje
   (unique_trip_instances, cada viaje-instancia se cuenta una sola vez).
 - Haversine se calcula una única vez por análisis completo en
   analyze(), y se reutiliza en la parada más cercana y en ambos radios.
 - frequencies.txt es opcional (GTFS lo permite): si falta, se asume
   un feed 100% de horario absoluto.
 - calendar.txt y calendar_dates.txt se aceptan en cualquier
   combinación válida (uno, otro o ambos); si faltan los dos, error
   explícito.
 - Se distingue nearest_physical_stop (la parada geográficamente más
   cercana que admite abordaje) de nearest_active_stop (la más cercana
   que además tiene viajes activos en la fecha consultada).
 - pickup_type == 1 se excluye de toda cuenta de oportunidades de
   abordaje (salidas, headway, ventana de servicio).
 - location_type y presencia real en stop_times.txt se usan para
   evitar seleccionar entradas, nodos u otras ubicaciones no
   abordables como si fueran paradas.
 - Zona horaria de generated_at coherente con agency_timezone (via
   zoneinfo), o renombrada a generated_at_utc si no hay agencia con
   zona declarada.
 - Serialización JSON estricta (allow_nan=False) tras convertir todo
   tipo NumPy/Pandas/NaN a tipos nativos o None.

Uso:
    python gtfs_analyzer_v1_2_1.py --gtfs RUTA --date YYYY-MM-DD \
        --lat LAT --lon LON --name "Nombre" [--export-json] [--out DIR]

    RUTA puede ser una carpeta con los .txt del feed o un archivo .zip
    (incluso con los .txt dentro de una subcarpeta del ZIP).
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import zipfile
from dataclasses import dataclass
from datetime import datetime, date as date_cls, timezone as dt_timezone
from typing import Dict, List, Optional, Tuple, Set

import numpy as np
import pandas as pd

try:
    from zoneinfo import ZoneInfo
except ImportError:  # pragma: no cover - Python < 3.9 no soportado
    ZoneInfo = None

# Archivos estrictamente obligatorios en cualquier feed GTFS válido para
# este programa. calendar.txt / calendar_dates.txt y frequencies.txt se
# validan aparte porque el estándar GTFS los admite en combinaciones
# distintas (ver _load y get_active_services).
REQUIRED_FILES = [
    "agency.txt",
    "routes.txt",
    "trips.txt",
    "stop_times.txt",
    "stops.txt",
    "feed_info.txt",
]

PEAK_WINDOWS = {
    "morning_peak": ("07:00:00", "09:00:00"),
    "afternoon_peak": ("17:00:00", "19:00:00"),
}

WEEKDAY_COLUMNS = [
    "monday", "tuesday", "wednesday", "thursday",
    "friday", "saturday", "sunday",
]

# location_type considerados "abordables" para búsquedas de parada. GTFS:
# 0 = parada/plataforma, 1 = estación (agregado, no abordable directo),
# 2 = entrada/salida, 3 = nodo genérico, 4 = zona de embarque.
BOARDABLE_LOCATION_TYPES = {"", "0"}
KNOWN_PICKUP_TYPES = {"", "0", "1"}


# --------------------------------------------------------------------------
# Utilidades de tiempo GTFS (soportan horas >= 24, p.ej. "25:32:00")
# --------------------------------------------------------------------------

_TIME_RE = re.compile(r"^\s*(\d{1,3}):([0-5]\d):([0-5]\d)\s*$")


def gtfs_time_to_seconds(t: str) -> int:
    """Convierte 'H:MM:SS' / 'HH:MM:SS' (incluyendo horas >=24) a segundos."""
    if t is None or (isinstance(t, float) and np.isnan(t)):
        raise ValueError("Hora GTFS vacía")
    m = _TIME_RE.match(str(t))
    if not m:
        raise ValueError(f"Formato de hora GTFS inválido: {t!r}")
    h, mm, ss = m.groups()
    return int(h) * 3600 + int(mm) * 60 + int(ss)


def seconds_to_gtfs_time(s: int) -> str:
    """Convierte segundos a 'HH:MM:SS', preservando horas >=24."""
    if s < 0:
        raise ValueError("No existen horas GTFS negativas")
    h = s // 3600
    m = (s % 3600) // 60
    sec = s % 60
    return f"{h:02d}:{m:02d}:{sec:02d}"


def describe_service_day_hour(h: int) -> Optional[str]:
    """Nota legible cuando la hora excede 24 (pertenece a la madrugada
    del día calendario siguiente, dentro del mismo día de servicio GTFS)."""
    if h >= 24:
        return (f"{h:02d} corresponde a las {h - 24:02d} horas del día "
                f"calendario siguiente, dentro del mismo día de servicio GTFS.")
    return None


# --------------------------------------------------------------------------
# Haversine vectorizado
# --------------------------------------------------------------------------

_EARTH_RADIUS_M = 6371000.0


def haversine_vectorized(lat1: float, lon1: float,
                          lat2: np.ndarray, lon2: np.ndarray) -> np.ndarray:
    """Distancia geodésica (línea recta) en metros, vectorizada con NumPy.

    Es una distancia geodésica, no una distancia caminable real: no
    considera calles, cruces, muros, vías férreas ni otras barreras.
    """
    lat1_r, lon1_r = np.radians(lat1), np.radians(lon1)
    lat2_r, lon2_r = np.radians(lat2), np.radians(lon2)
    dlat = lat2_r - lat1_r
    dlon = lon2_r - lon1_r
    a = np.sin(dlat / 2) ** 2 + np.cos(lat1_r) * np.cos(lat2_r) * np.sin(dlon / 2) ** 2
    return 2 * _EARTH_RADIUS_M * np.arcsin(np.sqrt(np.clip(a, 0, 1)))


# --------------------------------------------------------------------------
# Excepciones propias
# --------------------------------------------------------------------------

class GTFSDataError(Exception):
    """Datos GTFS ausentes, incompletos o inválidos."""


class DateOutOfServiceError(Exception):
    """La fecha consultada está fuera de la vigencia del feed."""


class NoStopsNearbyError(Exception):
    """No existen paradas abordables dentro del radio máximo de búsqueda."""


class InvalidCoordinateError(Exception):
    """Latitud o longitud fuera de rango válido."""


# --------------------------------------------------------------------------
# Analizador principal
# --------------------------------------------------------------------------

@dataclass
class GTFSAnalyzer:
    gtfs_path: str
    max_radius_m: float = 1000.0

    def __post_init__(self):
        self._load()

    # ---------------------- Carga y preparación --------------------------

    def _open_source(self) -> Dict[str, io.IOBase]:
        """Devuelve {nombre_archivo: objeto file-like}. Soporta carpetas y
        archivos .zip, incluso con los .txt dentro de una subcarpeta."""
        if os.path.isdir(self.gtfs_path):
            present = {name for name in os.listdir(self.gtfs_path)
                       if name.endswith(".txt")}
            missing = [f for f in REQUIRED_FILES if f not in present]
            if missing:
                raise GTFSDataError(
                    "Faltan archivos GTFS obligatorios en la carpeta: "
                    + ", ".join(missing)
                )
            return {
                name: open(os.path.join(self.gtfs_path, name), "rb")
                for name in present
            }
        elif zipfile.is_zipfile(self.gtfs_path):
            zf = zipfile.ZipFile(self.gtfs_path)
            # os.path.basename tolera .txt dentro de una subcarpeta del ZIP.
            names = {os.path.basename(n): n for n in zf.namelist()
                     if n.endswith(".txt")}
            missing = [f for f in REQUIRED_FILES if f not in names]
            if missing:
                raise GTFSDataError(
                    "Faltan archivos GTFS obligatorios en el ZIP: "
                    + ", ".join(missing)
                )
            return {name: io.BytesIO(zf.read(full))
                    for name, full in names.items()}
        else:
            raise GTFSDataError(
                f"'{self.gtfs_path}' no es una carpeta ni un archivo ZIP válido."
            )

    def _load(self):
        files = self._open_source()

        def read(name, **kw):
            return pd.read_csv(files[name], dtype=str, **kw)

        # -- feed_info.txt --------------------------------------------------
        self.feed_info = read("feed_info.txt")
        if self.feed_info.empty:
            raise GTFSDataError("feed_info.txt está vacío.")
        row = self.feed_info.iloc[0]
        self.feed_start_date = row["feed_start_date"]
        self.feed_end_date = row["feed_end_date"]
        self.feed_version = row.get("feed_version", "desconocida")

        # -- agency.txt / zona horaria ---------------------------------------
        self.agency = read("agency.txt")
        tz_col = self.agency.get("agency_timezone")
        distinct_tz = sorted({t for t in (tz_col.tolist() if tz_col is not None else [])
                               if isinstance(t, str) and t.strip()})
        self._timezone_ambiguous = False
        if len(distinct_tz) == 1:
            self.timezone = distinct_tz[0]
            self._timezone_source = "agency.txt"
        elif len(distinct_tz) > 1:
            self.timezone = distinct_tz[0]
            self._timezone_ambiguous = True
            self._timezone_source = "agency.txt (múltiples zonas; se usó la primera)"
        else:
            self.timezone = None  # sin zona declarada -> generated_at_utc
            self._timezone_source = "no declarada en agency.txt"

        # -- routes.txt / trips.txt ------------------------------------------
        self.routes = read("routes.txt")
        self.trips = read("trips.txt")

        # -- calendario: calendar.txt y/o calendar_dates.txt ------------------
        has_calendar = "calendar.txt" in files
        has_calendar_dates = "calendar_dates.txt" in files
        if not has_calendar and not has_calendar_dates:
            raise GTFSDataError(
                "El feed no tiene calendar.txt ni calendar_dates.txt: "
                "no hay ninguna definición de servicio disponible."
            )
        if has_calendar:
            self.calendar = read("calendar.txt")
            for col in WEEKDAY_COLUMNS + ["start_date", "end_date", "service_id"]:
                if col not in self.calendar.columns:
                    raise GTFSDataError(f"calendar.txt no tiene la columna '{col}'.")
        else:
            self.calendar = pd.DataFrame(
                columns=["service_id", *WEEKDAY_COLUMNS, "start_date", "end_date"])

        if has_calendar_dates:
            self.calendar_dates = read("calendar_dates.txt")
            for col in ("service_id", "date", "exception_type"):
                if col not in self.calendar_dates.columns:
                    raise GTFSDataError(f"calendar_dates.txt no tiene la columna '{col}'.")
        else:
            self.calendar_dates = pd.DataFrame(
                columns=["service_id", "date", "exception_type"])

        # -- frequencies.txt (OPCIONAL) ---------------------------------------
        if "frequencies.txt" in files:
            self.frequencies = read("frequencies.txt")
            for col in ("trip_id", "start_time", "end_time", "headway_secs"):
                if col not in self.frequencies.columns:
                    raise GTFSDataError(f"frequencies.txt no tiene la columna '{col}'.")
        else:
            self.frequencies = pd.DataFrame(
                columns=["trip_id", "start_time", "end_time", "headway_secs", "exact_times"])

        if self.frequencies.empty:
            self._freq_by_trip: Dict[str, List[dict]] = {}
        else:
            self.frequencies["headway_secs"] = self.frequencies["headway_secs"].astype(int)
            if "exact_times" not in self.frequencies.columns:
                self.frequencies["exact_times"] = "0"
            self.frequencies["exact_times"] = (
                self.frequencies["exact_times"].fillna("0").astype(str)
            )
            self.frequencies["start_s"] = self.frequencies["start_time"].map(gtfs_time_to_seconds)
            self.frequencies["end_s"] = self.frequencies["end_time"].map(gtfs_time_to_seconds)
            invalid = self.frequencies[self.frequencies["headway_secs"] <= 0]
            if not invalid.empty:
                sample = invalid.iloc[0]
                raise GTFSDataError(
                    "frequencies.txt contiene headway_secs <= 0 "
                    f"(trip_id={sample['trip_id']!r}, start_time={sample['start_time']!r}, "
                    f"end_time={sample['end_time']!r}, headway_secs={sample['headway_secs']!r}). "
                    "Esto es un error de datos y no se puede interpretar de forma segura."
                )
            self._freq_by_trip = {
                tid: g[["start_s", "end_s", "headway_secs", "exact_times"]].to_dict("records")
                for tid, g in self.frequencies.groupby("trip_id")
            }
            # índice ordinal estable de cada bloque dentro de su trip_id,
            # para poder construir identidades reproducibles
            # (trip_id, frequency_block_index, departure_instance_index).
            for tid, blocks in self._freq_by_trip.items():
                blocks.sort(key=lambda b: b["start_s"])

        # -- stops.txt --------------------------------------------------------
        self.stops = read(
            "stops.txt",
            usecols=lambda c: c in {
                "stop_id", "stop_name", "stop_lat", "stop_lon",
                "location_type", "parent_station",
            },
        )
        for col in ("stop_id", "stop_name", "stop_lat", "stop_lon"):
            if col not in self.stops.columns:
                raise GTFSDataError(f"stops.txt no tiene la columna obligatoria '{col}'.")
        self.stops["stop_lat"] = pd.to_numeric(self.stops["stop_lat"], errors="coerce")
        self.stops["stop_lon"] = pd.to_numeric(self.stops["stop_lon"], errors="coerce")
        bad_coords = self.stops["stop_lat"].isna() | self.stops["stop_lon"].isna()
        if bad_coords.any():
            self.stops = self.stops[~bad_coords].copy()
        if "location_type" not in self.stops.columns:
            self.stops["location_type"] = ""
        self.stops["location_type"] = self.stops["location_type"].fillna("").astype(str)
        self.stops = self.stops.reset_index(drop=True)

        self._stop_lat = self.stops["stop_lat"].to_numpy()
        self._stop_lon = self.stops["stop_lon"].to_numpy()

        # -- stop_times.txt -----------------------------------------------------
        self.stop_times = read(
            "stop_times.txt",
            usecols=lambda c: c in {
                "trip_id", "arrival_time", "departure_time",
                "stop_id", "stop_sequence", "pickup_type", "drop_off_type",
            },
        )
        for col in ("trip_id", "departure_time", "stop_id", "stop_sequence"):
            if col not in self.stop_times.columns:
                raise GTFSDataError(f"stop_times.txt no tiene la columna obligatoria '{col}'.")
        self.stop_times["stop_sequence"] = self.stop_times["stop_sequence"].astype(int)
        self.stop_times["departure_s"] = self.stop_times["departure_time"].map(gtfs_time_to_seconds)

        if "pickup_type" not in self.stop_times.columns:
            self.stop_times["pickup_type"] = ""
        self.stop_times["pickup_type"] = self.stop_times["pickup_type"].fillna("").astype(str)
        other_pickup = sorted(set(self.stop_times["pickup_type"]) - KNOWN_PICKUP_TYPES)
        self._other_pickup_types = other_pickup  # documentado en limitations
        # boardable=False sólo para pickup_type == "1" (sin abordaje);
        # otros valores GTFS (2=llamar a la agencia, 3=coordinar con
        # conductor) sí permiten abordar en la práctica y quedan True,
        # pero se documentan si aparecen.
        self.stop_times["boardable"] = self.stop_times["pickup_type"] != "1"

        # Primer despacho (offset 0) de cada trip: se calcula sobre TODAS
        # las filas (aborde permitido o no), porque el offset describe la
        # estructura horaria del viaje, no la posibilidad de abordar.
        first_idx = self.stop_times.groupby("trip_id")["stop_sequence"].idxmin()
        first_departure = self.stop_times.loc[first_idx].set_index("trip_id")["departure_s"]
        self.stop_times["first_departure_s"] = self.stop_times["trip_id"].map(first_departure)
        self.stop_times["offset_s"] = (
            self.stop_times["departure_s"] - self.stop_times["first_departure_s"]
        )

        # Paradas que efectivamente aparecen en stop_times.txt (para no
        # tratar entradas/nodos sin ningún viaje como si fueran paradas).
        self._stops_with_any_trip: Set[str] = set(self.stop_times["stop_id"].unique())

        # Máscara de paradas "abordables": location_type en {"", "0"} Y
        # con al menos una fila en stop_times.txt.
        boardable_location = self.stops["location_type"].isin(BOARDABLE_LOCATION_TYPES)
        boardable_presence = self.stops["stop_id"].isin(self._stops_with_any_trip)
        self._boardable_mask = (boardable_location & boardable_presence).to_numpy()

        # -- Índices auxiliares ------------------------------------------------
        route_cols = [c for c in ("route_id", "route_short_name", "route_long_name")
                      if c in self.routes.columns]
        self.trips_enriched = self.trips.merge(
            self.routes[route_cols], on="route_id", how="left"
        )
        for optional_col in ("route_short_name", "route_long_name",
                              "trip_headsign", "direction_id", "service_id"):
            if optional_col not in self.trips_enriched.columns:
                self.trips_enriched[optional_col] = pd.NA
        self._trip_info = self.trips_enriched.set_index("trip_id")
        self._trip_service = self.trips.set_index("trip_id")["service_id"]

        for f in files.values():
            try:
                f.close()
            except Exception:
                pass

    # ---------------------- Calendario / servicios activos ---------------

    def get_active_services(self, iso_date: str) -> List[str]:
        """Determina los service_id activos para una fecha ISO YYYY-MM-DD,
        combinando calendar.txt (si existe) con calendar_dates.txt (si
        existe). Admite feeds que sólo tengan uno de los dos archivos."""
        try:
            d = datetime.strptime(iso_date, "%Y-%m-%d").date()
        except ValueError:
            raise ValueError(f"Fecha inválida, use YYYY-MM-DD: {iso_date!r}")

        fs = self.feed_start_date
        fe = self.feed_end_date
        if d < date_cls(int(fs[:4]), int(fs[4:6]), int(fs[6:8])) or \
           d > date_cls(int(fe[:4]), int(fe[4:6]), int(fe[6:8])):
            raise DateOutOfServiceError(
                f"La fecha {iso_date} está fuera de la vigencia del feed "
                f"({fs} a {fe}, versión {self.feed_version})."
            )

        date_num = d.strftime("%Y%m%d")
        weekday_col = WEEKDAY_COLUMNS[d.weekday()]

        cal = self.calendar
        if not cal.empty:
            active = set(cal[
                (cal[weekday_col] == "1") &
                (cal["start_date"] <= date_num) &
                (cal["end_date"] >= date_num)
            ]["service_id"])
        else:
            active = set()

        cd = self.calendar_dates
        if not cd.empty:
            added = set(cd[(cd["date"] == date_num) & (cd["exception_type"] == "1")]["service_id"])
            removed = set(cd[(cd["date"] == date_num) & (cd["exception_type"] == "2")]["service_id"])
            active = (active | added) - removed
        return sorted(active)

    # ---------------------- Distancias -------------------------------

    def compute_distances(self, lat: float, lon: float) -> pd.Series:
        """Distancias geodésicas (m) de TODAS las paradas a (lat, lon),
        calculadas una sola vez por consulta, sin mutar self.stops."""
        if not (-90.0 <= lat <= 90.0):
            raise InvalidCoordinateError(f"Latitud fuera de rango [-90, 90]: {lat}")
        if not (-180.0 <= lon <= 180.0):
            raise InvalidCoordinateError(f"Longitud fuera de rango [-180, 180]: {lon}")
        dist = haversine_vectorized(lat, lon, self._stop_lat, self._stop_lon)
        return pd.Series(dist, index=self.stops.index, name="geodesic_distance_m")

    # ---------------------- Generación de salidas (con identidad) -------

    def _generate_departures_for_row(self, row) -> List[Tuple[int, bool, tuple]]:
        """Genera (segundos, es_estimada, identidad_instancia) para una
        fila boardable de stop_times, sin filtrar aún por ventana.

        La identidad permite deduplicar instancias de viaje reales:
          - viaje con frequencies.txt: (trip_id, block_index, n)
          - viaje con horario absoluto: (trip_id, "scheduled")
        """
        trip_id = row.trip_id
        offset = row.offset_s
        freq_rows = self._freq_by_trip.get(trip_id)
        out = []
        if freq_rows:
            for block_index, fr in enumerate(freq_rows):
                fs, fe, hw = fr["start_s"], fr["end_s"], fr["headway_secs"]
                is_estimated = fr["exact_times"] != "1"
                n = 0
                while fs + n * hw < fe:  # end_time exclusivo (GTFS estándar)
                    sec = fs + n * hw + offset
                    out.append((sec, is_estimated, (trip_id, block_index, n)))
                    n += 1
        else:
            out.append((row.departure_s, False, (trip_id, "scheduled")))
        return out

    def _weighted_headway_for_group(self, gdf: pd.DataFrame,
                                     s0: int, s1: int) -> Dict:
        """Headway ponderado por solapamiento temporal, desplazando cada
        bloque de frequencies.txt hasta la parada mediante su offset_s
        (corrección crítica 1 del encargo v1.2.1).

        También calcula, sobre las salidas efectivamente generadas dentro
        de la ventana, un intervalo efectivo = (última-primera)/(n-1), y
        documenta si hay más de un patrón trip_id concurrente (bloques de
        distintos trip_id que se solapan en el tiempo dentro de la
        ventana), caso en el que el promedio ponderado NO equivale a la
        frecuencia combinada real de los patrones simultáneos.
        """
        total_overlap = 0
        weighted = 0.0
        discrete_departures = 0
        intervals_at_stop = []  # (block_start_at_stop, block_end_at_stop, trip_id)
        generated_secs = []
        distinct_trip_ids = set()

        for row in gdf.itertuples(index=False):
            distinct_trip_ids.add(row.trip_id)
            freq_rows = self._freq_by_trip.get(row.trip_id)
            if freq_rows:
                for fr in freq_rows:
                    block_start_at_stop = fr["start_s"] + row.offset_s
                    block_end_at_stop = fr["end_s"] + row.offset_s
                    intervals_at_stop.append(
                        (block_start_at_stop, block_end_at_stop, row.trip_id))
                    s = max(block_start_at_stop, s0)
                    e = min(block_end_at_stop, s1)
                    if e > s:
                        overlap = e - s
                        total_overlap += overlap
                        weighted += overlap * fr["headway_secs"]
                        n = 0
                        while fr["start_s"] + n * fr["headway_secs"] < fr["end_s"]:
                            sec = fr["start_s"] + n * fr["headway_secs"] + row.offset_s
                            if s0 <= sec < s1:
                                generated_secs.append(sec)
                            n += 1
            else:
                if s0 <= row.departure_s < s1:
                    discrete_departures += 1
                    generated_secs.append(row.departure_s)

        # ¿Hay bloques de trip_id distintos que se solapan en el tiempo
        # dentro de la ventana consultada? (patrones concurrentes reales,
        # no simples bloques consecutivos del mismo patrón horario).
        concurrent = False
        relevant = [iv for iv in intervals_at_stop
                    if min(iv[1], s1) > max(iv[0], s0)]
        for i in range(len(relevant)):
            for j in range(i + 1, len(relevant)):
                if relevant[i][2] == relevant[j][2]:
                    continue
                s_i, e_i, _ = relevant[i]
                s_j, e_j, _ = relevant[j]
                if max(s_i, s_j) < min(e_i, e_j):
                    concurrent = True
                    break
            if concurrent:
                break

        generated_secs.sort()
        effective_interval = None
        if len(generated_secs) >= 2:
            effective_interval = round(
                (generated_secs[-1] - generated_secs[0]) / (len(generated_secs) - 1) / 60, 2)

        result = {
            "scheduled_departures": len(generated_secs),
            "distinct_trip_id_patterns": len(distinct_trip_ids),
            "concurrent_patterns_detected": concurrent,
        }
        if total_overlap > 0:
            result["weighted_block_headway_min"] = round(weighted / total_overlap / 60, 2)
            result["method"] = "headway_ponderado_por_solapamiento_temporal_con_offset"
        if effective_interval is not None:
            result["effective_interval_from_generated_departures_min"] = effective_interval
        if total_overlap == 0 and effective_interval is None and discrete_departures == 0:
            return None
        if concurrent:
            result["note"] = (
                "Se detectaron bloques de distintos trip_id del mismo "
                "route_id+direction_id solapados en el tiempo dentro de "
                "esta ventana. weighted_block_headway_min es un promedio "
                "ponderado de los headways declarados, NO la frecuencia "
                "combinada real de servicios simultáneos (que sería menor)."
            )
        return result

    # ---------------------- Parada más cercana (física y activa) --------

    def _closest_index(self, distances: pd.Series, mask: np.ndarray,
                        max_radius: float) -> Optional[int]:
        candidates = np.where(mask & (distances.to_numpy() <= max_radius))[0]
        if len(candidates) == 0:
            return None
        best = candidates[np.argmin(distances.to_numpy()[candidates])]
        return int(best)

    def _stop_summary(self, pos: int, distances: pd.Series) -> Dict:
        row = self.stops.iloc[pos]
        d = float(distances.iloc[pos])
        return {
            "stop_id": str(row["stop_id"]),
            "stop_name": str(row["stop_name"]),
            "geodesic_distance_m": round(d, 1),
            "theoretical_walk_time_min": round(d / 1.4 / 60, 1),
        }

    def _active_stop_ids(self, active_services: List[str]) -> Set[str]:
        """stop_id con al menos una fila boardable de un trip activo."""
        st = self.stop_times[self.stop_times["boardable"]]
        active_trip_mask = st["trip_id"].map(self._trip_service).isin(active_services)
        return set(st.loc[active_trip_mask, "stop_id"].unique())

    def nearest_physical_stop(self, distances: pd.Series) -> Dict:
        pos = self._closest_index(distances, self._boardable_mask, self.max_radius_m)
        if pos is None:
            raise NoStopsNearbyError(
                f"No hay paradas abordables dentro de {self.max_radius_m:.0f} m."
            )
        return self._stop_summary(pos, distances)

    def nearest_active_stop_position(self, distances: pd.Series,
                                      active_services: List[str]) -> Optional[int]:
        active_ids = self._active_stop_ids(active_services)
        if not active_ids:
            return None
        active_mask = self._boardable_mask & self.stops["stop_id"].isin(active_ids).to_numpy()
        return self._closest_index(distances, active_mask, self.max_radius_m)

    # ---------------------- Análisis de una parada activa concreta ------

    def _stop_detail(self, stop_pos: int, distances: pd.Series,
                      active_services: List[str]) -> Dict:
        stop_row = self.stops.iloc[stop_pos]
        stop_id = stop_row["stop_id"]
        summary = self._stop_summary(stop_pos, distances)

        st = self.stop_times[
            (self.stop_times["stop_id"] == stop_id) & (self.stop_times["boardable"])
        ].copy()
        st = st[st["trip_id"].map(self._trip_service).isin(active_services)]

        if st.empty:
            summary.update({
                "note": "No hay viajes activos abordables en esta parada para la fecha consultada.",
                "routes_serving_stop": [],
                "route_count": 0,
                "destinations": [],
                "service_window": None,
                "peak_departures": {},
                "headway_by_route_direction": [],
            })
            return summary

        st = st.merge(
            self._trip_info[["route_id", "route_short_name", "route_long_name",
                             "direction_id", "trip_headsign", "service_id"]],
            left_on="trip_id", right_index=True, how="left",
        )

        all_departures: List[Tuple[int, bool, tuple]] = []
        for row in st.itertuples(index=False):
            all_departures.extend(self._generate_departures_for_row(row))

        secs_only = [d[0] for d in all_departures]
        first_s, last_s = min(secs_only), max(secs_only)
        service_window = {
            "first_departure": seconds_to_gtfs_time(first_s),
            "last_departure": seconds_to_gtfs_time(last_s),
            "note": describe_service_day_hour(last_s // 3600),
        }

        peak_departures = {}
        for label, (t0, t1) in PEAK_WINDOWS.items():
            s0, s1 = gtfs_time_to_seconds(t0), gtfs_time_to_seconds(t1)
            in_window = [d for d in all_departures if s0 <= d[0] < s1]
            peak_departures[label] = {
                "window": f"{t0}-{t1}",
                "total_departures": len(in_window),
            }

        headway_rows = []
        for (route_id, direction_id), gdf in st.groupby(["route_id", "direction_id"]):
            route_short = gdf["route_short_name"].iloc[0]
            route_long = gdf["route_long_name"].iloc[0]
            headways = {}
            for label, (t0, t1) in PEAK_WINDOWS.items():
                s0, s1 = gtfs_time_to_seconds(t0), gtfs_time_to_seconds(t1)
                headways[label] = self._weighted_headway_for_group(gdf, s0, s1)
            headway_rows.append({
                "route_id": str(route_id),
                "route_short_name": None if pd.isna(route_short) else str(route_short),
                "route_long_name": None if pd.isna(route_long) else str(route_long),
                "direction_id": str(direction_id),
                "headway": headways,
            })

        dest_cols = ["route_id", "route_short_name", "route_long_name",
                     "direction_id", "trip_headsign", "service_id"]
        destinations = st[dest_cols].drop_duplicates().fillna("").to_dict("records")
        for dct in destinations:
            for k, v in dct.items():
                dct[k] = str(v)

        routes_serving_stop = sorted(set(st["route_id"].astype(str)))

        summary.update({
            "routes_serving_stop": routes_serving_stop,
            "route_count": len(routes_serving_stop),
            "destinations": destinations,
            "service_window": service_window,
            "peak_departures": peak_departures,
            "headway_by_route_direction": headway_rows,
        })
        return summary

    # ---------------------- Análisis de área (eventos vs instancias) ----

    def area_analysis(self, distances: pd.Series, radius_m: float,
                       active_services: List[str]) -> Dict:
        within_mask = (distances <= radius_m).to_numpy()
        within = self.stops[within_mask].copy()
        within_geo = distances[within_mask]
        stop_ids_area = set(within["stop_id"])

        if within.empty:
            empty_peak = {"window": None, "stop_departure_events": 0,
                          "unique_trip_instances": 0, "by_route": {}}
            return {
                "radius_m": radius_m,
                "stop_points": 0,
                "active_routes": [],
                "active_routes_count": 0,
                "morning_peak": empty_peak,
                "afternoon_peak": empty_peak,
            }

        st = self.stop_times[
            (self.stop_times["stop_id"].isin(stop_ids_area)) & (self.stop_times["boardable"])
        ].copy()
        st = st[st["trip_id"].map(self._trip_service).isin(active_services)]
        st = st.merge(
            self._trip_info[["route_id", "route_short_name", "direction_id"]],
            left_on="trip_id", right_index=True, how="left",
        )

        active_route_ids = sorted(set(st["route_id"].astype(str)))
        if "route_short_name" in self.routes.columns:
            route_name_map = self.routes.set_index("route_id")["route_short_name"].to_dict()
        else:
            route_name_map = {}
        active_routes_named = [
            {"route_id": rid,
             "route_short_name": (rid if pd.isna(route_name_map.get(rid, rid))
                                   else route_name_map.get(rid, rid))}
            for rid in active_route_ids
        ]

        peaks = {}
        for label, (t0, t1) in PEAK_WINDOWS.items():
            s0, s1 = gtfs_time_to_seconds(t0), gtfs_time_to_seconds(t1)
            events = []  # (identity, route_id)
            for row in st.itertuples(index=False):
                for sec, _est, identity in self._generate_departures_for_row(row):
                    if s0 <= sec < s1:
                        events.append((identity, row.route_id))

            stop_departure_events = len(events)
            unique_trip_instances = len({identity for identity, _ in events})

            by_route: Dict[str, Dict[str, int]] = {}
            for identity, route_id in events:
                rid = str(route_id)
                by_route.setdefault(rid, {"identities": set(), "events": 0})
                by_route[rid]["events"] += 1
                by_route[rid]["identities"].add(identity)
            by_route_out = {
                rid: {
                    "stop_departure_events": v["events"],
                    "unique_trip_instances": len(v["identities"]),
                }
                for rid, v in sorted(by_route.items())
            }

            peaks[label] = {
                "window": f"{t0}-{t1}",
                "stop_departure_events": stop_departure_events,
                "unique_trip_instances": unique_trip_instances,
                "by_route": by_route_out,
            }

        return {
            "radius_m": radius_m,
            "stop_points": int(len(within)),
            "active_routes": active_routes_named,
            "active_routes_count": len(active_routes_named),
            "morning_peak": peaks["morning_peak"],
            "afternoon_peak": peaks["afternoon_peak"],
        }

    # ---------------------- Análisis completo ---------------------------

    def analyze(self, lat: float, lon: float, iso_date: str, name: str = "") -> Dict:
        active_services = self.get_active_services(iso_date)

        # Haversine calculado UNA sola vez por análisis completo
        # (corrección crítica de rendimiento del encargo v1.2.1).
        distances = self.compute_distances(lat, lon)

        physical = self.nearest_physical_stop(distances)
        active_pos = self.nearest_active_stop_position(distances, active_services)

        if active_pos is None:
            active_detail = {
                "note": "No hay ninguna parada abordable con viajes activos "
                        f"dentro de {self.max_radius_m:.0f} m para la fecha consultada.",
                "routes_serving_stop": [], "route_count": 0, "destinations": [],
                "service_window": None, "peak_departures": {},
                "headway_by_route_direction": [],
            }
            same_as_physical = False
        else:
            active_detail = self._stop_detail(active_pos, distances, active_services)
            physical_pos_candidates = np.where(
                self.stops["stop_id"].to_numpy() == physical["stop_id"])[0]
            same_as_physical = bool(len(physical_pos_candidates) and
                                     physical_pos_candidates[0] == active_pos)
        active_detail["same_as_nearest_physical_stop"] = same_as_physical

        area_500 = self.area_analysis(distances, 500.0, active_services)
        area_800 = self.area_analysis(distances, 800.0, active_services)

        limitations = [
            "geodesic_distance_m es distancia geodésica en línea recta (Haversine); "
            "no es una ruta caminable real y puede haber calles sin cruce, vías "
            "férreas, muros, autopistas u otras barreras no consideradas.",
            "theoretical_walk_time_min asume 1.4 m/s (5 km/h) en línea recta; es "
            "una estimación teórica, no un tiempo de caminata verificado.",
            "Las salidas se derivan del horario programado del feed GTFS "
            "estático; no reflejan atrasos, adelantos ni cancelaciones en "
            "tiempo real.",
            "Cuando exact_times distinto de 1, las salidas generadas desde "
            "frequencies.txt son estimaciones de un viaje patrón, no el paso "
            "exacto observado del vehículo.",
            "stop_departure_events cuenta eventos de paso en cada parada del "
            "radio (un mismo vehículo puede sumar varios si su recorrido "
            "cruza más de una parada dentro del radio); unique_trip_instances "
            "cuenta cada instancia de viaje una sola vez. Ninguna de las dos "
            "debe leerse como 'cantidad de buses' sin esta aclaración.",
            "Se excluyeron de las cuentas de salida las filas de stop_times.txt "
            "con pickup_type=1 (sin abordaje permitido).",
            f"Feed vigente entre {self.feed_start_date} y {self.feed_end_date} "
            f"(versión {self.feed_version}); no incluye datos en tiempo real "
            f"ni fechas fuera de ese rango.",
        ]
        if self._other_pickup_types:
            limitations.append(
                "stop_times.txt contiene valores de pickup_type no documentados "
                f"explícitamente por este programa: {self._other_pickup_types}. "
                "Se trataron como abordaje permitido (solo pickup_type=1 se excluye)."
            )
        if self._timezone_ambiguous:
            limitations.append(
                f"agency.txt declara más de una zona horaria distinta; se usó "
                f"'{self.timezone}' ({self._timezone_source})."
            )

        metadata = {
            "feed_version": self.feed_version,
            "feed_start_date": self.feed_start_date,
            "feed_end_date": self.feed_end_date,
            "analysis_date": iso_date,
            "active_service_ids": active_services,
        }
        if self.timezone and ZoneInfo is not None:
            metadata["timezone"] = self.timezone
            metadata["generated_at"] = datetime.now(ZoneInfo(self.timezone)).isoformat()
        else:
            metadata["timezone"] = None
            metadata["generated_at_utc"] = datetime.now(dt_timezone.utc).isoformat()
            limitations.append(
                "No se encontró agency_timezone declarada en agency.txt; "
                "generated_at_utc se reporta en UTC en vez de una zona local."
            )

        return {
            "metadata": metadata,
            "location": {"name": name, "lat": float(lat), "lon": float(lon)},
            "nearest_physical_stop": physical,
            "nearest_active_stop": active_detail,
            # Alias de compatibilidad con v1.2: corresponde a la parada
            # ACTIVA (no a la física), tal como calculaba v1.2 al filtrar
            # por servicios activos antes de reportar horarios.
            "closest_stop_analysis": active_detail,
            "area_analysis": {
                "radius_500m": area_500,
                "radius_800m": area_800,
            },
            "limitations": limitations,
        }


# --------------------------------------------------------------------------
# Serialización JSON segura (sin objetos NumPy) y estricta (sin NaN)
# --------------------------------------------------------------------------

class NativeJSONEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, (np.integer,)):
            return int(o)
        if isinstance(o, (np.floating,)):
            return float(o)
        if isinstance(o, (np.bool_,)):
            return bool(o)
        if isinstance(o, (np.ndarray,)):
            return o.tolist()
        if isinstance(o, set):
            return sorted(o)
        return super().default(o)


def to_native(obj):
    """Convierte recursivamente tipos NumPy/Pandas a tipos nativos de Python,
    y cualquier NaN/NaT/pd.NA a None, para permitir allow_nan=False."""
    if isinstance(obj, dict):
        return {k: to_native(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple, set)):
        return [to_native(v) for v in obj]
    if isinstance(obj, (np.integer,)):
        return int(obj)
    if isinstance(obj, (np.floating,)):
        val = float(obj)
        return None if (np.isnan(val) or np.isinf(val)) else val
    if isinstance(obj, (np.bool_,)):
        return bool(obj)
    if isinstance(obj, (pd.Timestamp,)):
        return obj.isoformat()
    if obj is pd.NA:
        return None
    if isinstance(obj, float) and (obj != obj or obj in (float("inf"), float("-inf"))):
        return None
    return obj


def dumps_strict(obj, **kwargs) -> str:
    """json.dumps con allow_nan=False: si algo no serializable o NaN se
    coló pese a to_native(), falla de forma ruidosa en vez de producir un
    JSON técnicamente inválido (NaN no es JSON estándar)."""
    kwargs.setdefault("ensure_ascii", False)
    kwargs.setdefault("indent", 2)
    kwargs.setdefault("cls", NativeJSONEncoder)
    kwargs.setdefault("allow_nan", False)
    return json.dumps(obj, **kwargs)


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def build_arg_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Analizador de accesibilidad a transporte público (GTFS Red Metropolitana)."
    )
    p.add_argument("--gtfs", required=True,
                   help="Ruta a la carpeta GTFS extraída o a un archivo .zip.")
    p.add_argument("--date", required=True,
                   help="Fecha de consulta en formato ISO YYYY-MM-DD.")
    p.add_argument("--lat", type=float, required=True, help="Latitud (-90 a 90).")
    p.add_argument("--lon", type=float, required=True, help="Longitud (-180 a 180).")
    p.add_argument("--name", default="", help="Nombre descriptivo de la ubicación.")
    p.add_argument("--export-json", action="store_true",
                   help="Exporta el resultado como archivo JSON.")
    p.add_argument("--out", default=".",
                   help="Carpeta de salida para el JSON exportado (se crea si no existe).")
    p.add_argument("--interactive", action="store_true",
                   help="Modo interactivo: solicita coordenadas por consola.")
    return p


def _ensure_out_dir(out_dir: str):
    try:
        os.makedirs(out_dir, exist_ok=True)
    except OSError as e:
        raise GTFSDataError(f"No se pudo crear/usar la carpeta de salida '{out_dir}': {e}")


def run_single(analyzer: GTFSAnalyzer, lat: float, lon: float, iso_date: str,
               name: str, export_json: bool, out_dir: str) -> Dict:
    result = to_native(analyzer.analyze(lat, lon, iso_date, name))
    print(dumps_strict(result))
    if export_json:
        _ensure_out_dir(out_dir)
        safe_name = re.sub(r"[^\w\-]+", "_", name or f"{lat}_{lon}").strip("_")
        filename = f"analysis_{safe_name}_{iso_date}.json"
        path = os.path.join(out_dir, filename)
        with open(path, "w", encoding="utf-8") as f:
            f.write(dumps_strict(result))
        print(f"\n✅ Guardado en: {path}", file=sys.stderr)
    return result


def interactive_mode(analyzer: GTFSAnalyzer, iso_date: str, out_dir: str):
    print("Ingresa coordenadas 'lat, lon' o 'salir' para terminar.")
    while True:
        raw = input("Coordenada (lat, lon): ").strip()
        if raw.lower() in {"salir", "exit", "quit"}:
            break
        parts = raw.split(",")
        if len(parts) != 2:
            print("Formato inválido. Usa: lat, lon")
            continue
        try:
            lat, lon = float(parts[0]), float(parts[1])
        except ValueError:
            print("Formato inválido. Usa: lat, lon")
            continue
        try:
            run_single(analyzer, lat, lon, iso_date, "", True, out_dir)
        except (InvalidCoordinateError, NoStopsNearbyError, DateOutOfServiceError,
                GTFSDataError) as e:
            print(f"Error: {e}")


def main(argv=None):
    args = build_arg_parser().parse_args(argv)
    try:
        analyzer = GTFSAnalyzer(args.gtfs)
    except GTFSDataError as e:
        print(f"❌ Error de datos GTFS: {e}", file=sys.stderr)
        sys.exit(2)

    if args.interactive:
        interactive_mode(analyzer, args.date, args.out)
        return

    try:
        run_single(analyzer, args.lat, args.lon, args.date, args.name,
                   args.export_json, args.out)
    except InvalidCoordinateError as e:
        print(f"❌ Coordenada inválida: {e}", file=sys.stderr)
        sys.exit(2)
    except DateOutOfServiceError as e:
        print(f"❌ Fecha fuera de vigencia: {e}", file=sys.stderr)
        sys.exit(2)
    except NoStopsNearbyError as e:
        print(f"❌ Sin paradas cercanas: {e}", file=sys.stderr)
        sys.exit(2)
    except GTFSDataError as e:
        print(f"❌ Error de datos GTFS: {e}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
