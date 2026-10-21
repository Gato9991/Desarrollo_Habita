# Pruebas del Índice HABITA — aire anual

Versión **0.3.0-prueba.1**, pendiente de aprobación. Calcula los cuatro parámetros de HABITA con los archivos reales del repositorio. Primero construye el índice anual de aire de cada estación; después cada ubicación recibe el índice de la estación admisible más cercana.

Esta versión incorpora la indicación de Daniel de usar **un resumen anual para aire**. Conserva los pesos globales de 25 % y los pesos internos del Word 0.2.0; actualiza el período de aire y normaliza la unidad ppb del ozono. Bienestar térmico conserva los cuatro meses recopilados.

## Ejecutar en Windows

Descarga y extrae una copia actualizada de todo Desarrollo_Habita. En PowerShell, situado en su carpeta raíz:

~~~powershell
python -m pip install -r .\Pruebas\requirements.txt
python .\Pruebas\probar_habita.py
~~~

Se usa **python** porque el equipo de Daniel reconoce Python 3.13.5 y no tiene disponible el lanzador py. Se necesita Python 3.10 o posterior. En Linux/macOS puedes usar python3.

El programa muestra la tabla en consola, abre el informe HTML y guarda los resultados en Pruebas/resultados/. Para ejecutar sin abrir el navegador:

~~~powershell
python .\Pruebas\probar_habita.py --no-abrir
~~~

También puedes usar INICIAR_PRUEBA.bat después de instalar la dependencia.

### Consultar cualquier coordenada

Sin argumentos se evalúan las ocho ubicaciones originales. Para una ubicación:

~~~powershell
python .\Pruebas\probar_habita.py --latitud -33.4422475 --longitud -70.653898 --nombre "Mi ubicación"
~~~

Se admiten también --lat y --lon. Latitud y longitud se entregan juntas y en campos separados. El informe se regenera en la carpeta de salida. Puedes elegir otra con --salida.

**[Ver resultados de referencia](RESULTADOS.md)** · **[Ver los índices anuales por estación](INDICES_AIRE.md)**.

## Cómo accede a otras carpetas

Las rutas parten de la raíz del repositorio, deducida desde el script mediante pathlib; no dependen de la carpeta actual de la terminal.

| Entrada | Uso |
| --- | --- |
| FUENTES/CONFORT TERMICO/\*/\*.xlsx | 127 Excel de 16 estaciones. Selecciona la única hoja con Fecha, Hora (UTC) y Ts (°C) o HR (%); permite la hoja de respaldo Texto original. |
| FUENTES/fuentes climaticas usadas/\*.csv | 19 CSV de aire. Selecciona validado; si falta, preliminar etiquetado. Descarta la columna no validada. |
| FUENTES/fuentes cercania a establecimiento de salud/ESTABLECIMIENTOS_12062026_DES.zip | Catálogo MINSAL de junio de 2026: lee DBF, comprueba WGS84 y filtra establecimientos. |
| HABITA_GTFS_Movilidad_v1.2.1_GUI/data/manifest.json y sus nueve partes .zip.partNN | Reconstruye el GTFS en memoria y comprueba tamaño y SHA256 antes de leer sus tablas. |
| Pruebas/estaciones_dmc.json y estaciones_aire.json | Coordenadas, correspondencia entre estaciones y archivos, unidades y enlaces oficiales de evidencia. |

El cálculo no necesita internet ni credenciales. Escribe en la carpeta de salida y conserva los archivos fuente. La app Android y el analizador de movilidad existente son componentes separados.

La extracción de ZIP en Windows puede quitar puntos finales de nombres de carpetas. El lector admite esa diferencia sin alterar las planillas.

## Aire: un índice anual por estación

### Período

La ventana común es **14-10-2025 a 13-10-2026**, inclusive: **365 fechas esperadas**. El corte del día 13 permite incluir Las Condes con el mismo fin. En las otras series el primer día de la ventana puede estar ausente; se registra y no se rellena.

Los CSV contienen filas fechadas por día. Esta versión construye un **resumen anual de sus puntajes diarios**, no interpreta cada fila como un promedio anual ni utiliza una media oficial anual ya calculada por SINCA.

Se conserva la fecha diaria de la fuente, sin convertirla a UTC ni reconstruir observaciones horarias. Los CSV antiguos de Cerrillos I y Quilicura I quedan fuera del período. El archivo histórico de El Bosque se recorta a la ventana y no arrastra mediciones de 1997.

### Unidades y conversión

Las fichas de las doce estaciones se revisaron el 21-10-2026. MP2,5 está en **µg/m³** y O₃ en **ppb**. El archivo estaciones_aire.json conserva la ficha y el enlace a la serie diaria de cada variable.

Las curvas originales trabajan en µg/m³. Para conservarlas, O₃ se convierte mediante la ley de gas ideal, con referencia de producto **25 °C, 101.325 Pa y masa molar 48 g/mol**:

~~~text
O3_ug_m3 = O3_ppb × 101325 × 48 / (8.31446261815324 × 298.15) × 0.001
1 ppb de O3 ≈ 1.962 µg/m³ en esa referencia.
~~~

Es una referencia declarada de normalización del prototipo; no representa la temperatura o presión realmente observada en cada estación. Se conserva tanto la unidad y media original en ppb como la conversión. Los límites de HABITA no se presentan como umbrales normativos.

### Cálculo y calidad

1. Recortar las filas a la ventana anual.
2. Por marca: priorizar validado sobre preliminar. Excluir no validados, números inválidos y concentraciones negativas.
3. Cada fecha cuenta una vez. Duplicados idénticos no añaden días; valores incompatibles del mismo estado excluyen la fecha.
4. Convertir O₃ a la escala de referencia y puntuar cada registro diario.
5. Calcular la media simple de los puntajes diarios admisibles de cada contaminante.
6. Exigir **al menos 70 % de los 365 días**, es decir 256 días, para utilizar ese componente.
7. Combinar **70 % MP2,5 y 30 % O₃**. Si falta un componente, conservar explícita la cobertura interna y usar la regla de faltantes.

El 70 % es un umbral operativo provisional: extiende al período anual el control de días del modelo anterior. No acredita representatividad estacional ni valida por sí solo los datos. La media de concentraciones que muestra el informe es descriptiva; el índice se obtiene promediando puntajes diarios, por lo que no se sustituye ese orden.

Esta ejecución deja **9 estaciones con al menos un componente utilizable**, de 12 recopiladas. El Bosque no alcanza cobertura anual; Cerrillos I y Quilicura I no tienen datos en la ventana. Los demás faltantes y los estados preliminares permanecen visibles.

### Asignación por cercanía

Para la coordenada del usuario se elige la estación con puntaje disponible de menor distancia Haversine, hasta 30 km. Empates: menor código. Se copia exactamente su índice de aire y su cobertura; no se vuelve a promediar con estaciones vecinas ni se interpola.

Haversine usa radio terrestre 6.371.000 m. No se necesita Dijkstra: no se busca una ruta en una red de calles. Los dos contaminantes permanecen en la estación seleccionada; no se completa un ozono faltante con otra estación.

## Cuatro parámetros y sus pesos

Puntajes internos de 0 a 100; la salida muestra 0 a 10. L(x) limita al intervalo [0,100].

| Parámetro · 25 % de base | Funciones de los componentes | Pesos internos |
| --- | --- | --- |
| Aire | MP2,5: L(100×(1−MP/100)); O₃ normalizado: L(100×(1−O3/200)). Resumen anual de puntajes diarios. | 70 % / 30 % |
| Paraderos | Distancia: L(100×(1−d/1000)); cantidad en 500 m: min(100,10×n). | 60 % / 40 % |
| Salud | Distancia: L(100×(1−d/5000)); cantidad en 2 km: min(100,20×n). | 70 % / 30 % |
| Bienestar térmico | Temperatura: L(100×(1−abs(T−22)/18)); humedad: L(100×(1−abs(HR−50)/50)). | 60 % / 40 % |

Bienestar térmico usa **01-07-2026 a 13-10-2026**. Puntúa observaciones y agrega por hora, día y mes, con 18 horas por día, 70 % de días por mes y al menos dos meses. Los meses admisibles pesan igual. Selecciona su estación más cercana de forma independiente del aire. Temperatura en °C y humedad en %; Talagante mantiene humedad en revisión y Río Clarillo conserva su ausencia de septiembre.

Paraderos: stop_id único, location_type vacío/0, ruta de bus route_type 3 y abordaje permitido en stop_times. No incluye Metro, frecuencia ni calendario activo. Por eso los conteos pueden diferir de los JSON del analizador general de movilidad.

Salud: estado vigente en operación habitual, prestador Público/Privado, coordenadas válidas y deduplicación por COD_VIG. Incluye los once tipos clínicos del Word: Hospital, Clínica, Centro de Salud Privado, CESFAM, CECOSF, PSR, CGU, CGR, SAPU, SAR y SUR. Quedan 3.690 establecimientos nacionales; buscar en todo el catálogo evita cortar vecinos por el límite de la RM.

Para paraderos y salud, el vecino más cercano se busca en todo el catálogo admisible, incluso fuera del radio de conteo. Todas las distancias son en línea recta, no caminatas ni tiempos de viaje.

## Global y cobertura

c_i es la suma de los pesos internos de los componentes disponibles. S_i es su puntaje ponderado renormalizado; queda Sin dato si c_i es cero.

~~~text
C = suma(0.25 × c_i)
HABITA = suma(0.25 × c_i × S_i) / C
participación final de i = 0.25 × c_i / C
~~~

Las sumas omiten los parámetros ausentes. Solo se muestra un global con C ≥ 0,75. **La cobertura es disponibilidad ponderada de componentes**, no exactitud ni porcentaje de días.

Si únicamente falta O₃, c_aire = 0,70 y C = **92,5 %**. Si los ocho componentes están disponibles, C = 100 %. La cobertura temporal anual se muestra por separado; un componente puede pasar el mínimo sin tener 365 días completos.

## Salidas y trazabilidad

- resultados_habita.html: tabla de ubicaciones, tabla anual de estaciones, detalle expandible y archivos leídos.
- resultados_habita.csv: puntajes y cobertura por ubicación.
- indices_aire_estaciones.csv: medias originales, días admisibles, índice y cobertura de cada estación.
- detalle_habita.json: reglas, períodos, conversión, estados, estaciones y auditoría completa.

La carpeta ejemplo_resultados/ conserva una ejecución de referencia. Para ver el HTML hay que descargarlo y abrirlo; GitHub no ejecuta páginas HTML de un repositorio.

El JSON registra rutas, tamaños y SHA256 de los bytes realmente leídos. El commit de referencia de los archivos fuente es ee17d1d6b471619d2f50a214fe588a1cb3b267f7; los hashes identifican cambios locales posteriores.

## Validación

~~~powershell
python -m unittest discover -s .\Pruebas -p "test_*.py" -v
~~~

Las pruebas cubren los ejemplos del Word, conversión del ozono, año y límite de cobertura, validación y duplicados, fuentes intactas, estaciones y distancias, ejecución desde otra carpeta y una consulta real por coordenadas.

## Referencias para unidades y conversión

- [SINCA — Parque O'Higgins](https://sinca.mma.gob.cl/index.php/estacion/index/id/273): unidades y series disponibles. Cada estación conserva su propia evidencia en estaciones_aire.json.
- [SINCA — serie diaria MP2,5](https://sinca.mma.gob.cl/cgi-bin/APUB-MMA/apub.htmlindico2.cgi?page=pageFrame&header=Parque%20O%27Higgins&macropath=./RM/D14/Cal/PM25&macro=PM25.diario.diario&from=251014&to=261013).
- [EPA — guía AERMOD](https://nepis.epa.gov/Exe/ZyPURL.cgi?Dockey=P100QVCE.TXT): conversión de O₃ desde ppb a µg/m³ con referencia de 25 °C y 1013,25 mb.
