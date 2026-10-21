# Resultados de referencia — HABITA 0.2.0-prueba.1

Ejecución con archivos reales del repositorio. **Prueba pendiente de aprobación. Globales parciales: 75 % de cobertura.**

Período: **1 de julio a 13 de octubre de 2026**. Se conserva el orden entregado por Daniel; no es un ranking. Todos los puntajes se muestran de 0 a 10.

| Ubicación | Aire | Paraderos | Salud | Bienestar térmico | HABITA parcial | Cobertura |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Palacio de La Moneda | Sin dato | 8,8 | 9,8 | 4,8 | 7,8 | 75 % |
| Costanera Center (Cenco Costanera) | Sin dato | 9,4 | 9,6 | 5,0 | 8,0 | 75 % |
| Estadio Nacional Julio Martínez Prádanos | Sin dato | 8,2 | 9,6 | 5,0 | 7,6 | 75 % |
| Aeropuerto Internacional Arturo Merino Benítez | Sin dato | 6,0 | 3,3 | 4,7 | 4,7 | 75 % |
| Cerro San Cristóbal — acceso Pío Nono | Sin dato | 6,5 | 9,0 | 4,8 | 6,8 | 75 % |
| Hospital Dr. Sótero del Río | Sin dato | 9,6 | 9,9 | 4,7 | 8,1 | 75 % |
| Estación Intermodal La Cisterna | Sin dato | 9,8 | 9,7 | 4,7 | 8,1 | 75 % |
| Observatorio Astronómico Nacional — Cerro Calán | Sin dato | 2,8 | 8,7 | 5,0 | 5,5 | 75 % |

Aire permanece sin puntaje porque los CSV no acreditan la unidad ni el estadístico de su exportación. No se infiere un valor ni se penaliza la ubicación por la ausencia. El global parcial redistribuye el peso disponible: paraderos, salud y bienestar participan con un tercio cada uno.

[Informe HTML con desglose](ejemplo_resultados/resultados_habita.html) · [CSV](ejemplo_resultados/resultados_habita.csv) · [JSON con auditoría completa](ejemplo_resultados/detalle_habita.json)

El HTML se descarga y se abre en el navegador. Para recalcular: `py -3 Pruebas/probar_habita.py`. [Instrucciones, fórmulas y acceso a las fuentes](README.md).

Validación: **15 pruebas correctas**, incluyendo los dos ejemplos del Word y la referencia de distancia GTFS de Estadio Nacional. Se leyeron 127 Excel, 19 CSV, 3.690 establecimientos de salud admisibles y 12.141 paraderos de bus. El GTFS reconstruido coincide con el SHA256 de su manifest.

Estos archivos son una fotografía de la ejecución, no reemplazan las fuentes ni el script. El JSON incluye los hashes de los archivos realmente leídos.
