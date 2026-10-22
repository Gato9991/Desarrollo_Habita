# HABITA Android

Aplicación nativa en Kotlin y Jetpack Compose. La versión **0.2.0** incorpora aire, confort exterior y salud junto al transporte. Se preparó desde `main` del repositorio del equipo, commit `e075fd4fd0a4c893f419ea3b7baec09aa878f38f`, el **21 de octubre de 2026**. Los avances de Android se comparten en la rama [`kr0us`](https://github.com/Gato9991/Desarrollo_Habita/tree/kr0us) y se integran en `main` mediante la revisión del equipo.

## Abrir y ejecutar

1. Abre **HABITA_Android** en Android Studio, conservando el resto del repositorio a su lado.
2. Usa el JDK integrado **21**, instala **Android SDK 35** si se solicita y sincroniza Gradle. La primera sincronización requiere internet.
3. Selecciona el módulo **app** y un emulador o teléfono **Android 8 / API 26 o superior**. Pulsa **Run**.
4. En Inicio, pulsa **Evaluar una propiedad**. El modo **Índice del entorno** admite dirección confirmada mediante el geocodificador Android o coordenadas. Los ejemplos funcionan sin geocodificación.
5. Pulsa **Analizar ubicación** para calcular los cuatro indicadores con las fuentes incluidas. En Índice, desplázate hasta **Fuentes de este punto** para ver estaciones, períodos y centros de salud cercanos.
6. Pulsa **Actualizar fuentes en línea** para consultar clima exterior, aire modelado, establecimientos de salud y la capa de peligros volcánicos. Se muestran fecha, proveedor, errores y enlace de cada consulta. Estas consultas no modifican el índice histórico.

No hacen falta Python, un servidor local ni claves API para ejecutar esta versión. `local.properties` y las cachés son locales; el Gradle Wrapper está incluido. Para compilar, se conservan las nueve partes y el `manifest.json` de `HABITA_GTFS_Movilidad_v1.2.1_GUI/data`: la tarea de Gradle reconstruye el feed y verifica su SHA-256.

Dos carpetas de fuentes DMC eliminan el punto final de su nombre para permitir el checkout en Windows. Los 16 Excel mantienen sus bytes originales.

## Tres modos identificados

| Modo | Datos y escala |
| --- | --- |
| **Índice del entorno** | Modelo `Pruebas/modelo.py`, `0.3.0-prueba.1`: aire, confort exterior, paraderos y salud, 25% de base cada uno; resultados 0–10. Es una propuesta de prueba pendiente de aprobación del equipo. |
| **Dirección / GTFS** | Análisis de recorridos y horarios programados por fecha de servicio. Conserva el modelo anterior de transporte parcial, 20% de disponibilidad y escala 0–100, para mantener el recorrido y las comparaciones existentes. |
| **Demostración** | Seis puntos simulados del prototipo anterior, con cinco variables y escala 0–100. No se asignan estos datos a direcciones reales. |

Las escalas y metodologías se muestran en la interfaz y en los reportes. Los resultados de modos diferentes no deben compararse como un único modelo.

## Fuentes históricas que calculan el índice

| Indicador | Fuente incluida y reglas |
| --- | --- |
| Aire | 19 CSV SINCA, 12 estaciones recopiladas y 9 con al menos un componente admisible. Ventana **14/10/2025–13/10/2026**. Media anual de puntajes diarios, mínimo **256 de 365 días** por contaminante. Prioridad a validados, preliminares identificados y no validados excluidos. |
| Confort exterior | **127 Excel de 16 estaciones**, DMC y redes asociadas. Ventana **01/07/2026–13/10/2026**. Puntajes por observación, agregados por hora, día y mes; 18 horas/día, 70% de días/mes y al menos dos meses. Meses admisibles con igual peso. La humedad de Talagante permanece en revisión; septiembre de Río Clarillo sigue ausente. |
| Salud | Catálogo MINSAL de junio de 2026, **3.690 establecimientos nacionales admisibles**. Once tipos clínicos, operación habitual, prestador público/privado, coordenadas válidas y código único. Distancia al vecino más cercano y cantidad en 2 km. |
| Paraderos | **12.141 paraderos físicos de buses** del feed del equipo: `location_type` vacío/0, `route_type=3` y abordaje permitido en `stop_times`. Distancia al vecino más cercano y cantidad en 500 m. No filtra por calendario de servicio. |

Aire y confort seleccionan su estación admisible más cercana de forma independiente, hasta **30 km**. No se interpolan estaciones ni se completa el ozono de una estación con otra. Salud y paraderos buscan al vecino más cercano en todo su catálogo admisible; los radios se aplican al conteo. Las distancias son Haversine, en línea recta.

El O₃ de SINCA está en **ppb**. Los lectores originales lo convierten a µg/m³ con referencia explícita de **25 °C y 1 atm** antes de puntuarlo. Las medias de concentraciones son descriptivas: se conserva el orden **puntuar registros y después agregarlos**, sin reemplazarlo por puntuar una media. Las ausencias reducen la cobertura; nunca se rellenan con cero.

El índice global se muestra solo con **75% o más de disponibilidad ponderada de componentes**. La cobertura no significa exactitud, porcentaje de días ni validación científica. Se conservan el detalle, la procedencia y los SHA-256 de los archivos leídos en el reporte JSON.

## APIs complementarias

| Servicio | Información y límites |
| --- | --- |
| [Open-Meteo Weather](https://open-meteo.com/en/docs) | Temperatura y humedad exterior actuales, modeladas. No equivalen al interior de una vivienda ni al resumen histórico DMC. |
| [Open-Meteo Air Quality / Copernicus CAMS](https://open-meteo.com/en/docs/air-quality-api) | MP2,5 y O₃ modelados; dominio global de aproximadamente **45 km**, con fecha del proveedor. Ambos vienen en µg/m³. No son mediciones de una estación SINCA ni un índice anual. |
| [IDE Chile / MINSAL WFS](https://geoportal.cl/geoserver/EstablecimientosdesaluddeChile2026/wfs?request=GetCapabilities) | La capa publicada actualmente corresponde a junio de 2026. Se consultan hasta 5 km y se cuentan los centros admisibles en 2 km. Mismos once tipos clínicos; se rechazan respuestas truncadas. No confirma cupos, atención ni horarios. |
| [SENAPRED](https://visor-grd.senapred.gob.cl/arcgis/rest/services/SIIE_AMENAZAS/amenazas/MapServer/2) | Intersección del punto con la capa de peligros volcánicos. No se transforma en un porcentaje de exposición ni se suma al índice. Sin coincidencias no significa ausencia de riesgo. |

Las consultas se ejecutan al pulsar el botón, con HTTPS, límites de tamaño y tiempos de espera. Un proveedor caído no elimina el índice ni los resultados de los otros proveedores. Se conserva la última consulta del entorno en almacenamiento privado y cada resultado muestra cuándo se consultó. Al cambiar coordenadas se exige volver a analizar para no mezclar lugares.

Open-Meteo ofrece acceso gratuito sin clave para uso no comercial; la aplicación atribuye a Open-Meteo y Copernicus CAMS. Antes de un uso comercial hay que revisar sus condiciones. No se guardan credenciales en el código.

La API oficial DMC requiere usuario y token. Esta actualización utiliza sus Excel auditados y ofrece Open-Meteo como consulta actual alternativa. El JSON público de SINCA mezcla índices ICAP con información presentada en HTML, por lo que no se interpreta su valor gráfico como una concentración. Ver [decisiones y límites de integración](docs/INTEGRACION_FUENTES.md).

## Movilidad y reportes

Se conserva el motor GTFS local: calendario y excepciones, horas superiores a 24:00, frecuencias con fin exclusivo, recorridos y destinos, punta mañana/tarde, radios de 500/800 m, eventos por parada y viajes únicos. El feed incluido **V167.20260829** tiene vigencia **29/08/2026–31/12/2026**. No proporciona llegadas en tiempo real. La caminata es teórica a 1,4 m/s.

Se pueden importar ZIP o carpetas GTFS mediante el selector de Android y restaurar el feed incluido. Una importación afecta **Movilidad**; el índice histórico del entorno conserva el catálogo de buses versionado del equipo para seguir siendo reproducible.

Los informes JSON, PDF y el resumen compartido indican el modo, la escala, la cobertura y las fuentes. El JSON conserva el detalle completo y las consultas de APIs. El PDF incluye un resumen legible y las limitaciones. Storage Access Framework permite guardarlos sin acceso general al almacenamiento.

## Verificación y actualización de fuentes

Desde `HABITA_Android`, con `JAVA_HOME` apuntando al JDK integrado:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

El APK local queda en `app/build/outputs/apk/debug/app-debug.apk`. Es de desarrollo, para pruebas; no está publicado en Play Store.

Con un dispositivo de pruebas conectado, las regresiones que no necesitan internet:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.notClass=cl.habita.mobile.OnlineSourcesDeviceTest"
```

La comprobación manual de los cuatro servicios en Android necesita internet:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=cl.habita.mobile.OnlineSourcesDeviceTest"
```

Para incorporar nuevas fuentes históricas, desde la raíz del repositorio, con Python y la dependencia de `Pruebas/requirements.txt`:

```powershell
python HABITA_Android/tools/prepare_reference.py
```

El preparador reutiliza los lectores y reglas de `Pruebas`; genera el catálogo compacto, su checksum y las ocho referencias para pruebas Kotlin. No modifica los archivos originales. Android Studio utiliza ese catálogo ya preparado y verifica el checksum, sin ejecutar Python.

Ver [validación](docs/VALIDACION.md). Las pruebas del núcleo conservan las comparaciones GTFS e índice de la versión anterior y añaden equivalencia de los ocho resultados geográficos Python, selección de estaciones, límites de cobertura, unidades, duplicados y APIs incompletas.

## Alcance pendiente

Las amenazas de inundación, fallas y remoción en masa requieren capas con cobertura, fechas y reglas de interpretación validadas. Esta versión no inventa sus puntajes ni afirma que la consulta volcánica cubra todas las amenazas. El modelo nuevo de cuatro indicadores aún es una prueba del equipo. También queda pendiente la integración IoT de 48–72 horas dentro de la vivienda.

HABITA, proyecto fundado por Mauricio Medina, con participación de Daniel Morales, Cristian Morales y Jordan Navia en su etapa de desarrollo.
