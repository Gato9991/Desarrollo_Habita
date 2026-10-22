# Integración de fuentes del entorno

Revisión del 21/10/2026 sobre `main`, commit `e075fd4fd0a4c893f419ea3b7baec09aa878f38f`. Los avances se comparten en la rama `kr0us` del repositorio del equipo.

## Decisión de modelo

`Pruebas/README.md` y `Pruebas/modelo.py` introducen `0.3.0-prueba.1`: cuatro parámetros con 25% de base y salida 0–10. El propio repositorio lo declara una propuesta pendiente de aprobación. Se implementa como un modo explícito, **Índice del entorno**, manteniendo la demostración y el análisis GTFS anterior.

La app calcula para cualquier coordenada usando los catálogos completos. No se limita a mostrar los ocho resultados de ejemplo: estos se usan para contrastar Kotlin con Python. Se mantienen los criterios de agregación temporal y la selección de estaciones del programa de referencia.

## Fuentes oficiales y alternativas verificadas

- **Salud:** el [geoportal MINSAL](https://www.minsal.cl/ide-minsal-geoportal-descarga-de-datos/) identifica la descarga WGS84 del 12/06/2026, que corresponde al ZIP del repositorio. El [servicio WFS de IDE Chile](https://geoportal.cl/geoserver/EstablecimientosdesaluddeChile2026/wfs?request=GetCapabilities) devuelve actualmente el tipo `EstablecimientosdesaluddeChile2026:establecimientos_de_salud_junio_2026`. Su ficha de catálogo aún menciona febrero: la app identifica la edición de la capa realmente devuelta. La consulta online usa filtros clínicos y estado, deduplicación por código y Haversine; limita el vecino online a 5 km, mientras el vecino del índice se busca en todo el catálogo nacional.
- **Confort:** la [documentación DMC](https://climatologia.meteochile.gob.cl/application/documentacion/getDocumento/16) exige usuario y token. Los Excel del equipo permiten un índice reproducible sin exponer una clave personal en el APK. [Open-Meteo](https://open-meteo.com/en/docs) proporciona temperatura y humedad actuales sin clave para uso no comercial; se muestra como condición exterior modelada y separada del índice histórico.
- **Aire:** las fichas SINCA enlazadas en `Pruebas/estaciones_aire.json` acreditan unidades. El JSON del mapa SINCA respondió, pero sus valores gráficos incluyen ICAP y etiquetas HTML: no se toma ese número por µg/m³. La [API de Open-Meteo Air Quality](https://open-meteo.com/en/docs/air-quality-api) entrega concentraciones explícitas de MP2,5 y O₃ de Copernicus CAMS. En Chile usa el dominio global, de unos 45 km; no debe presentarse como una lectura precisa en la dirección ni como una estación oficial local.
- **Amenazas:** la [capa oficial SENAPRED](https://visor-grd.senapred.gob.cl/arcgis/rest/services/SIIE_AMENAZAS/amenazas/MapServer/2) permite consultar intersecciones con polígonos de peligro volcánico. No cubre por sí misma inundación, remoción en masa o fallas. La respuesta no se normaliza a 0–100 ni se usa para afirmar seguridad.
- **Transporte:** se conserva GTFS estático y el análisis de servicios programados. No se declara una integración con llegadas en tiempo real. El índice nuevo cuenta buses físicos abordables; Movilidad usa sus reglas y calendario existentes.

## Fechas, unidades y faltantes

La fecha del formulario GTFS no modifica la ventana anual SINCA ni el período DMC. Las APIs actuales muestran la fecha del proveedor y la hora UTC de consulta. No completan componentes históricos ausentes. O₃ SINCA se normaliza desde ppb bajo la referencia declarada por el modelo; O₃ CAMS ya está en µg/m³ y no se convierte otra vez.

Las medias que muestra la ficha de una estación son descriptivas. Los puntajes se obtienen de la agregación de puntajes de las observaciones originales. No se puntúa una media como atajo. La humedad de Talagante en revisión, las ausencias y los días insuficientes siguen ausentes.

Una respuesta con valores faltantes, unidades incompatibles, error HTTP, tamaño excesivo, JSON inválido o catálogo truncado queda identificada como no disponible. No se reemplaza por cero. Se guardan los resultados por ubicación para evitar reutilizar silenciosamente información de otro punto.

## Compatibilidad Windows

Dos nombres de carpeta de `main` terminaban en punto y Git para Windows rechazaba el checkout: `Curacaví Ad.` y `Eulogio Sánchez, Tobalaba Ad.`. La corrección elimina únicamente ese punto del nombre de las carpetas. Los 16 Excel conservan exactamente sus blobs originales. El lector de `Pruebas` ya admite ambos nombres. La integración de estos cambios en `main` permite que otros computadores Windows puedan clonar el repositorio normalmente.

## Actualización reproducible

`tools/prepare_reference.py` ejecuta los lectores de `Pruebas` sobre las fuentes locales, conserva hashes, genera `app/src/main/assets/reference/catalog.json` y prepara ocho referencias de regresión. El catálogo se incluye en el APK y se verifica al compilar y al cargar. Las actualizaciones de los archivos originales requieren regenerarlo y repetir las pruebas. Las consultas en línea conservan URL, proveedor, fecha, hora de consulta y error por proveedor cuando corresponde.
