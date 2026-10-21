# Resultados de referencia — HABITA 0.3.0-prueba.1

**Aire anual activado. Prueba pendiente de aprobación.** Cada ubicación recibe el puntaje de aire de la estación admisible más cercana.

Aire: **14-10-2025 a 13-10-2026**, 365 fechas esperadas. Bienestar térmico: **01-07-2026 a 13-10-2026**. Los puntajes se muestran de 0 a 10, en el orden entregado por Daniel.

| Ubicación | Estación de aire | Aire | Paraderos | Salud | Bienestar térmico | HABITA | Cobertura |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Palacio de La Moneda | Parque O'Higgins (273) | 7,9 | 8,8 | 9,8 | 4,8 | 7,8 | 92,5 % |
| Costanera Center (Cenco Costanera) | Parque O'Higgins (273) | 7,9 | 9,4 | 9,6 | 5,0 | 8,0 | 92,5 % |
| Estadio Nacional Julio Martínez Prádanos | Parque O'Higgins (273) | 7,9 | 8,2 | 9,6 | 5,0 | 7,6 | 92,5 % |
| Aeropuerto Internacional Arturo Merino Benítez | Pudahuel (190) | 7,8 | 6,0 | 3,3 | 4,7 | 5,5 | 100 % |
| Cerro San Cristóbal — acceso Pío Nono | Parque O'Higgins (273) | 7,9 | 6,5 | 9,0 | 4,8 | 7,0 | 92,5 % |
| Hospital Dr. Sótero del Río | Puente Alto (233) | 8,0 | 9,6 | 9,9 | 4,7 | 8,0 | 100 % |
| Estación Intermodal La Cisterna | Cerrillos II (290) | 7,8 | 9,8 | 9,7 | 4,7 | 8,0 | 92,5 % |
| Observatorio Astronómico Nacional — Cerro Calán | Las Condes (239) | 8,3 | 2,8 | 8,7 | 5,0 | 6,1 | 92,5 % |

**92,5 %** indica que falta el componente O₃ de la estación asignada. Los demás componentes están disponibles. **100 %** indica disponibilidad de los ocho componentes; no implica 365 días completos ni certeza del resultado. Cada serie informa aparte sus días aceptados y estados de validación.

[Índices anuales de las doce estaciones](INDICES_AIRE.md) · [HTML con desglose](ejemplo_resultados/resultados_habita.html) · [CSV de ubicaciones](ejemplo_resultados/resultados_habita.csv) · [CSV de estaciones](ejemplo_resultados/indices_aire_estaciones.csv) · [JSON con auditoría](ejemplo_resultados/detalle_habita.json).

Para abrir el HTML, descárgalo. Para recalcular: `python Pruebas/probar_habita.py`. [Instrucciones y metodología](README.md).

Validación: **26 pruebas correctas**. Se leyeron 127 Excel, 19 CSV de aire, 3.690 establecimientos de salud admisibles y 12.141 paraderos de bus. Se verificó el SHA256 del GTFS reconstruido.
