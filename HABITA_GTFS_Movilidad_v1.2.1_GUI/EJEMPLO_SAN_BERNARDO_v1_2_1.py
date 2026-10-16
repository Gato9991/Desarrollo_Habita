#!/usr/bin/env python3
"""
Ejemplo territorial HABITA v1.2.1
=================================

Ejecuta el analizador v1.2.1 sobre los dos casos territoriales
oficiales del proyecto: Plaza de Armas de San Bernardo y Estadio
Nacional Julio Martínez Prádanos.

Uso:
    python EJEMPLO_SAN_BERNARDO_v1_2_1.py --gtfs RUTA_AL_ZIP_O_CARPETA [--date YYYY-MM-DD]
"""

import argparse
import os
import sys

from gtfs_analyzer_v1_2_1 import GTFSAnalyzer, to_native, dumps_strict

# Plaza de Armas de San Bernardo
PLAZA_SAN_BERNARDO = (-33.5922368, -70.704706)

# Estadio Nacional Julio Martínez Prádanos
ESTADIO_NACIONAL = (-33.4644994, -70.6112756)

FECHA_CONTROL = "2026-09-23"

UBICACIONES = {
    "Plaza de Armas - San Bernardo": PLAZA_SAN_BERNARDO,
    "Estadio Nacional Julio Martínez Prádanos": ESTADIO_NACIONAL,
}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--gtfs", required=True, help="Carpeta GTFS extraída o archivo .zip")
    p.add_argument("--date", default=FECHA_CONTROL, help=f"Fecha ISO (default: {FECHA_CONTROL})")
    p.add_argument("--out", default=".", help="Carpeta de salida para los JSON")
    args = p.parse_args()

    print(f"Cargando feed GTFS desde: {args.gtfs}")
    analyzer = GTFSAnalyzer(args.gtfs)
    print(f"Feed {analyzer.feed_version} vigente {analyzer.feed_start_date}-{analyzer.feed_end_date}\n")

    os.makedirs(args.out, exist_ok=True)

    for nombre, (lat, lon) in UBICACIONES.items():
        print(f"{'='*72}\n{nombre}  ({lat}, {lon})\n{'='*72}")
        result = to_native(analyzer.analyze(lat, lon, args.date, nombre))

        phys = result["nearest_physical_stop"]
        act = result["nearest_active_stop"]
        print(f"Parada física más cercana: {phys['stop_id']} - {phys['stop_name']}")
        print(f"  Distancia geodésica: {phys['geodesic_distance_m']} m "
              f"(tiempo teórico caminando: {phys['theoretical_walk_time_min']} min)")
        if act.get("same_as_nearest_physical_stop"):
            print("  La parada activa más cercana coincide con la física.")
        else:
            print(f"  ⚠️  La parada ACTIVA más cercana es distinta: "
                  f"{act.get('stop_id', 'ninguna')}")
        if act.get("service_window"):
            print(f"  Ventana de servicio: {act['service_window']['first_departure']} "
                  f"-> {act['service_window']['last_departure']}")
            print(f"  Recorridos en esta parada: {act['route_count']}")

        a500 = result["area_analysis"]["radius_500m"]
        a800 = result["area_analysis"]["radius_800m"]
        print(f"  Cobertura: {a500['stop_points']} paradas en 500m, "
              f"{a800['stop_points']} en 800m")
        print(f"  Recorridos activos en 800m ({args.date}): {a800['active_routes_count']}")
        mp = a800["morning_peak"]
        print(f"  Punta mañana 800m: {mp['stop_departure_events']} eventos de salida "
              f"/ {mp['unique_trip_instances']} instancias únicas de viaje")

        filename = f"analysis_{nombre.replace(' ', '_')}_{args.date}_v1_2_1.json"
        path = os.path.join(args.out, filename)
        with open(path, "w", encoding="utf-8") as f:
            f.write(dumps_strict(result))
        print(f"  -> Guardado en: {path}\n")


if __name__ == "__main__":
    sys.exit(main())
