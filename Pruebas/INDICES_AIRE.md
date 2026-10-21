# Índice anual de aire por estación

Versión **0.3.0-prueba.1**, pendiente de aprobación. Ventana: **14-10-2025 a 13-10-2026**, 365 fechas esperadas.

Cada contaminante necesita al menos 256 días admisibles (70 %). La estación combina MP2,5 al 70 % y O₃ al 30 %. El puntaje se calcula primero por estación y luego se asigna por cercanía al usuario. Las medias originales y la conversión de O₃ quedan en el CSV y JSON.

| Código | Estación | Días MP2,5 /365 | Días O₃ /365 | Aire /10 | Cobertura de componentes |
| --- | --- | ---: | ---: | ---: | ---: |
| 116 | Cerrillos I | 0 | 0 | Sin dato | 0 % |
| 290 | Cerrillos II | 355 | 0 | 7,8 | 70 % |
| 228 | Cerro Navia | 359 | 257 | 7,9 | 100 % |
| 260 | El Bosque | 155 | 0 | Sin dato | 0 % |
| 262 | La Florida | 356 | 257 | 7,9 | 100 % |
| 239 | Las Condes | 284 | 122 | 8,3 | 70 % |
| 190 | Pudahuel | 356 | 259 | 7,8 | 100 % |
| 233 | Puente Alto | 359 | 258 | 8,0 | 100 % |
| 271 | Quilicura | 360 | 0 | 8,0 | 70 % |
| 149 | Quilicura I | 0 | 0 | Sin dato | 0 % |
| 273 | Parque O'Higgins | 359 | 248 | 7,9 | 70 % |
| 197 | Talagante | 350 | 0 | 8,1 | 70 % |

**Nueve estaciones** tienen un puntaje utilizable. Cerrillos I y Quilicura I no tienen datos en la ventana. El Bosque no alcanza el mínimo anual. Las estaciones con cobertura del 70 % tienen MP2,5 utilizable y O₃ ausente o insuficiente; su puntaje de aire se calcula con MP2,5 y el global conserva esa falta de cobertura.

Los valores no validados se excluyen; los preliminares se admiten y se etiquetan. La fracción de días observados es distinta de la cobertura de componentes de esta tabla.

[CSV con medias, días e índices](ejemplo_resultados/indices_aire_estaciones.csv) · [Resultados por ubicación](RESULTADOS.md) · [Metodología y fuentes](README.md).
