# HABITA · Versión móvil Android

Aplicación nativa en **Kotlin + Jetpack Compose**, dentro de una carpeta independiente del resto del repositorio. Conserva el recorrido del [Figma de Cristian Morales](https://www.figma.com/design/nHfZIyxzjzNRKXqNqoWuqX/HABITA?node-id=0-1): **Inicio → Evaluar propiedad → Índice → Detalle → Reporte**, e incorpora movilidad. Usa la paleta de las interfaces web del equipo: azul marino `#062441`, azul `#096899`, turquesa `#00B9C3` y fondo `#F3F7F9`.

## Abrir en Android Studio

1. Descarga o clona **la rama `main` del [repositorio del equipo](https://github.com/Gato9991/Desarrollo_Habita)**. Conserva la carpeta hermana `HABITA_GTFS_Movilidad_v1.2.1_GUI/data` con las nueve partes del feed y su `manifest.json`.
2. En Android Studio selecciona **File → Open → HABITA_Android**. Abre esta carpeta, no la raíz del repositorio.
3. Usa el **JDK integrado de Android Studio (21)** y permite la sincronización de Gradle. El proyecto compila contra Android SDK 35; Android Studio puede instalar ese SDK si falta. La primera sincronización requiere internet.
4. Selecciona el módulo **app** y un emulador o teléfono con **Android 8.0 / API 26 o superior**.
5. Pulsa **Run ▶**. No necesitas Python, un servidor en el computador, una cuenta de Hábita ni claves API para la demo y el análisis GTFS.

`local.properties` es local y está ignorado por Git: Android Studio lo crea con tu ruta de SDK. No lo subas. El Gradle Wrapper está incluido.

Para clonar desde una terminal:

```text
git clone --branch main https://github.com/Gato9991/Desarrollo_Habita.git
```

Después abre `Desarrollo_Habita/HABITA_Android` en Android Studio. Si ya tienes el repositorio clonado, actualiza tu rama `main` desde el repositorio del equipo antes de abrirlo. Las nueve partes GTFS ya están versionadas: no hay que descargar fuentes adicionales para ejecutar la demo.

## Qué funciona

| Función | Comportamiento |
|---|---|
| Inicio y navegación | Pantallas nativas, navegación inferior, botón volver, desplazamiento y estado durante cambios de configuración. |
| Seis puntos de prueba | Los mismos SB-01, SB-02, PA-01, PA-02, PE-01 y PE-02 de la demo web, con los mismos valores y ausencias. Se recuerda el punto elegido. |
| Índice HABITA | Cinco variables, pesos internos, componentes desplegables, disponibilidad ponderada y reajuste por datos faltantes. Cero no se confunde con ausencia. |
| Ubicaciones reales | Consulta por nombre y coordenadas; ejemplos de Plaza de Armas de San Bernardo y Estadio Nacional. La fecha se valida contra la vigencia del feed. |
| Búsqueda de direcciones | Usa `android.location.Geocoder`, muestra coincidencias y permite confirmar una. Si el dispositivo no ofrece ese servicio o no tiene conexión, se pueden ingresar coordenadas. |
| Motor GTFS local | Paradero físico y activo más cercanos hasta 1.000 m, Haversine, caminata teórica, recorridos, sentidos, destinos, servicios, primera y última salida. |
| Horarios | Horas superiores a 24:00, calendario y excepciones, bloques de frecuencias con fin exclusivo y desplazamiento hasta la parada. Se excluye `pickup_type=1`. |
| Punta mañana y tarde | 07:00–09:00 y 17:00–19:00; salidas, intervalos ponderados y efectivos, y aviso de patrones simultáneos. |
| Cobertura | Radios de 500 y 800 m, puntos de parada, recorridos, eventos por parada e instancias únicas de viaje, con desglose por recorrido. |
| Esquema de proximidad | Dibujo nativo de los puntos de parada reales del feed, con radios de referencia. Se distingue de un mapa de calles. Puede abrir la ubicación en otra app de mapas. |
| Fuente GTFS | ZIP o carpeta elegidos con el selector de documentos Android, hasta 300 MB; validación antes del reemplazo y opción de restaurar el feed incluido. |
| Reportes | PDF nativo paginado, JSON completo y resumen para compartir. Se conserva el último análisis de movilidad en almacenamiento privado. |
| Carga y errores | Operaciones en segundo plano, progreso, cancelación y errores legibles para coordenadas, fechas, archivos y geocodificación. |

## Lectura correcta de los resultados

**El índice y sus ponderaciones siguen siendo provisionales. Los seis puntos de demostración usan datos simulados. No representan direcciones reales, mediciones verificadas ni una certificación de seguridad o habitabilidad.**

La app adapta los indicadores del Figma a las **cinco variables actuales del repositorio**: calidad del aire, confort térmico, accesibilidad a paraderos, accesibilidad a salud y amenazas naturales. No genera puntajes de ruido o seguridad a partir de otras variables.

En una consulta real, GTFS permite calcular **solo el componente de accesibilidad a paraderos**. Ese índice parcial muestra **20% de cobertura**; aire, confort, salud y amenazas permanecen sin datos. Para este componente se cuentan paradas físicas abordables presentes en `stop_times.txt` dentro de 500 m; el conteo general `stop_points` de los informes GTFS es otro indicador y no se usa indistintamente. Un índice parcial no es directamente comparable con uno completo.

El feed incluido declara **V167.20260829**, con vigencia **29/08/2026–31/12/2026**. Los horarios son **programados**, sin llegadas en tiempo real. Las distancias son en línea recta; la caminata supone 1,4 m/s y no sigue calles. Eventos por parada y viajes únicos son conteos distintos, y ninguno garantiza una cantidad de vehículos físicos.

La integración de fuentes reales de aire, confort, salud y amenazas y la conexión a sensores IoT de 48–72 horas siguen pendientes en el proyecto del equipo. La app conserva el contexto del Figma y marca esa medición como una etapa futura.

## APIs y dependencias

- **Geocoder de Android** para resolver direcciones. Su implementación depende del dispositivo y puede usar un servicio de red. Se consulta solo al pulsar «Buscar dirección». No requiere una clave configurada en este proyecto. [Referencia oficial](https://developer.android.com/reference/android/location/Geocoder).
- **Storage Access Framework** para importar ZIP/carpetas y guardar PDF/JSON, sin solicitar acceso general a los archivos del teléfono.
- **PdfDocument** para generar PDF nativo, e intents Android para compartir texto o abrir mapas.
- **GTFS estático** procesado con Kotlin/JVM, `ZipFile` y un lector CSV en flujo. El motor no usa Python ni consulta un servicio externo.
- **Jetpack Compose / Material 3** para las pantallas, y Gson para JSON. Las versiones quedan fijadas en Gradle.

La web original todavía no integra una API de Google Maps Platform ni un backend público de Hábita. No se incluyen llamadas a servicios inexistentes ni claves privadas. El enlace a mapas abre una aplicación externa; no es una integración con Geocoding API de Google Maps Platform.

## Estructura

```text
HABITA_Android/
├── app/                         Aplicación Android
│   └── src/
│       ├── main/java/cl/habita/mobile/
│       │   ├── MainActivity.kt
│       │   ├── HabitaViewModel.kt
│       │   ├── ReportPdf.kt
│       │   ├── data/            Fuente GTFS, documentos y persistencia
│       │   └── ui/              Pantallas, componentes y colores
│       └── androidTest/         Pruebas de navegación y ejecución nativa
├── core/                        Cálculos Kotlin sin dependencia Android
│   └── src/
│       ├── main/kotlin/cl/habita/core/
│       │   ├── HabitaIndex.kt
│       │   ├── GtfsAnalyzer.kt
│       │   └── Csv.kt
│       └── test/                Reglas y comparación con informes Python
└── gradle/wrapper/               Gradle Wrapper
```

La tarea `prepareBundledGtfs` reconstruye el ZIP del equipo desde sus nueve partes, verifica tamaño y SHA-256 y lo incluye dentro del APK. Así el teléfono funciona sin el computador y Git no duplica esos 12 MB. **Si se mueve únicamente esta carpeta, también debe conservarse la carpeta de datos hermana en la misma ubicación relativa.**

## Compilar y verificar

Desde una terminal situada en `HABITA_Android`, con `JAVA_HOME` apuntando al JDK integrado:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. Es una compilación de desarrollo firmada con la clave de depuración del equipo; no es una publicación en Play Store.

Ver el [resultado de la validación](docs/VALIDACION.md) y las [capturas obtenidas en Android](docs/capturas/).

Con un emulador o teléfono de pruebas conectado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Las pruebas del núcleo comparan los seis resultados del índice con el motor Python, comprueban datos faltantes y entradas inválidas, y contrastan **parada física, parada activa y cobertura completas** con los informes originales de San Bernardo y Estadio Nacional del 23/09/2026. También prueban frecuencias, offsets, fin exclusivo, calendario por excepciones, restricciones de abordaje y viajes únicos. Los JSON de referencia se conservan sin alterar en `core/src/test/resources`.

Las pruebas Android recorren el diseño móvil, verifican estado tras recrear la actividad, ejecutan GTFS en el dispositivo, abren las vistas de recorridos/cobertura/ficha técnica y comprueban la generación de PDF. Sus capturas se escriben en el directorio externo privado de la app, subcarpeta `qa/`.

## Colaborar

La versión compartida se encuentra en `main` del [repositorio del equipo](https://github.com/Gato9991/Desarrollo_Habita). Para nuevos cambios, crea una rama desde `main` actualizado y sigue las reglas de revisión del equipo. Sube código, pruebas y documentación; las carpetas `build`, `.gradle`, `.idea`, `.kotlin` y `local.properties` quedan excluidas porque se generan en cada computador. No hacen falta APK, cachés, rutas locales del SDK ni claves de firma para abrir y compilar el proyecto.

HABITA, proyecto fundado por Mauricio Medina, con participación de Daniel Morales, Cristian Morales y Jordan Navia en su etapa de desarrollo.
