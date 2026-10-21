# Pruebas del Índice HABITA

Prueba reproducible para las **ocho coordenadas entregadas por Daniel**. Implementa la propuesta del Word **Modelación del Índice HABITA, versión 0.2.0**, con cuatro parámetros. **Pendiente de evaluación; no es la implementación oficial.**

## Ejecutar

Necesitas Python 3.10 o posterior y una copia completa de Desarrollo_Habita. Desde la raíz del repositorio:

~~~powershell
py -3 -m pip install -r Pruebas/requirements.txt
py -3 Pruebas/probar_habita.py
~~~

En Linux/macOS, usa python3 en lugar de py -3. En Windows también puedes abrir **INICIAR_PRUEBA.bat**, después de instalar la dependencia. El script deduce la raíz desde su ubicación; no depende de la carpeta actual de la terminal.

Muestra una **tabla cuadriculada en consola** y abre una **tabla HTML** en el navegador. Guarda además CSV y JSON en Pruebas/resultados/. Para ejecutar sin abrir el navegador:

~~~powershell
py -3 Pruebas/probar_habita.py --no-abrir
~~~

El cálculo funciona sin internet ni credenciales. Las fichas oficiales son enlaces de consulta, no descargas durante la ejecución. Solo escribe en la carpeta de salida; conserva intactas las fuentes. No modifica la aplicación Android ni el analizador GTFS existente.

**[Ver los resultados calculados de referencia](RESULTADOS.md)**. La carpeta ejemplo_resultados/ conserva HTML, CSV y JSON de una ejecución real. Para ver el HTML, descárgalo y ábrelo; GitHub no ejecuta las páginas HTML de un repositorio.

## Qué lee de las otras carpetas

| Entrada del repositorio | Uso |
| --- | --- |
| FUENTES/CONFORT TERMICO/\*/\*.xlsx | Las 127 planillas de 16 estaciones. Selecciona la única hoja con Fecha, Hora (UTC) y Ts (°C) o HR (%); permite una hoja adicional de respaldo Texto original. |
| FUENTES/fuentes climaticas usadas/\*.csv | Los 19 CSV SINCA: fechas, valores alternativos validados/preliminares/no validados y metadatos de exportación. |
| FUENTES/fuentes cercania a establecimiento de salud/ESTABLECIMIENTOS_12062026_DES.zip | Catálogo MINSAL de junio de 2026; lee el DBF y comprueba la proyección WGS84. |
| HABITA_GTFS_Movilidad_v1.2.1_GUI/data/manifest.json y sus nueve partes .zip.partNN | Reconstruye el GTFS en memoria, verifica tamaño y SHA256, y lee stops, routes, trips y stop_times. |
| Pruebas/estaciones_dmc.json y estaciones_aire.json | Coordenadas verificadas en las fichas oficiales el 21-10-2026; correspondencia entre estaciones y archivos; metadatos pendientes explícitos. |

No reutiliza puntajes precalculados de otra dirección. Las rutas se resuelven con pathlib, a partir de la raíz del repositorio. La extracción de ZIP en Windows puede quitar puntos finales a las carpetas: el lector admite esa diferencia, sin cambiar los archivos.

El JSON enumera los archivos realmente leídos, sus tamaños y SHA256, las reglas, el período, la estación elegida y la auditoría de todas las estaciones. El commit de referencia de las fuentes es **ee17d1d6b471619d2f50a214fe588a1cb3b267f7**; los hashes permiten detectar cambios locales posteriores.

## Qué se calcula

Todos los componentes se puntúan de 0 a 100; la presentación divide por 10 y redondea solo al mostrar. L(x) limita x al intervalo [0,100].

| Parámetro · peso base 25 % | Componentes y funciones | Pesos internos |
| --- | --- | --- |
| Aire | MP2,5: L(100×(1−MP/100)); O₃: L(100×(1−O₃/200)). Requiere µg/m³ compatibles y metadatos confirmados. | 70 % / 30 % |
| Paraderos | Distancia: L(100×(1−d/1000)); cantidad en 500 m: min(100,10×n). | 60 % / 40 % |
| Salud | Distancia: L(100×(1−d/5000)); cantidad en 2 km: min(100,20×n). | 70 % / 30 % |
| Bienestar térmico | Temperatura: L(100×(1−abs(T−22)/18)); humedad: L(100×(1−abs(HR−50)/50)). | 60 % / 40 % |

Los límites numéricos conservan el prototipo anterior. Son parámetros provisionales de HABITA.

### Estaciones y tiempo

- Ventana: **01-07-2026 a 13-10-2026**, inclusive. Octubre tiene 13 días esperados.
- Aire y bienestar eligen, por separado, la estación más cercana con al menos un componente admisible, a un máximo de 30 km. Empates: menor código.
- Ambos componentes de un parámetro permanecen en la misma estación. No se toma humedad de una segunda estación para completar la primera.
- Se puntúa cada observación antes de promediar. Para series horarias: observaciones → media de puntajes por hora → media por día → media por mes → media de meses admisibles.
- Se exigen 18 horas distintas por día, 70 % de días por mes y al menos dos meses admisibles por componente. Los meses pesan igual. No se convierten cuatro meses en un promedio anual.
- DMC usa días UTC. Para SINCA debe acreditarse la agregación y el huso original del CSV. Un horario 0000 no demuestra un promedio diario.
- Duplicados idénticos no suman mediciones; una marca con valores incompatibles se excluye. Ceros explícitos válidos cuentan como datos.
- Talagante mantiene humedad en revisión. Río Clarillo conserva su ausencia de humedad de septiembre. No se rellenan meses.

### Paraderos y salud

Distancias Haversine con radio terrestre 6.371.000 m. El vecino más cercano se busca en todo el catálogo admisible; el radio de conteo no limita esa búsqueda.

Paraderos: stop_id único, location_type 0/vacío, vinculado a una ruta de bus (route_type 3), con presencia que permita abordaje en stop_times. Se excluyen accesos, estaciones contenedoras, nodos y Metro. No depende del calendario de viajes ni cuenta frecuencias. **Por este filtro, el conteo puede diferir de los JSON del analizador de movilidad que incluyen Metro.** En Estadio Nacional, el más cercano sigue siendo PD561 a 308 m; esta prueba cuenta 20 paraderos de bus dentro de 500 m.

Salud: estado Vigente en Operación Habitual, prestador Público o Privado, coordenadas válidas, deduplicación por COD_VIG. Se incluyen los once tipos clínicos de la sección 6 del Word: Hospital, Clínica, Centro de Salud Privado, CESFAM, CECOSF, PSR, CGU, CGR, SAPU, SAR y SUR. El filtro entrega 3.690 establecimientos nacionales. La búsqueda nacional evita cortar vecinos en el límite de la RM. No se cuentan laboratorios o clínicas dentales como atención clínica general.

### Faltantes y cobertura

c_i es la suma de pesos internos disponibles del parámetro. Su puntaje S_i es la media ponderada de sus componentes disponibles. Si no tiene componentes, queda Sin dato.

- Cobertura global C = suma(0,25×c_i).
- HABITA = suma(0,25×c_i×S_i) / C, omitiendo parámetros ausentes.
- Participación final del parámetro = 0,25×c_i / C.
- Publicar un global solo con C ≥ 0,75. Con menos cobertura se muestran los parámetros disponibles y **Cobertura insuficiente**.
- Los meses utilizados y la validación se informan por separado: C no mide certeza ni porcentaje de horas observadas.

## Situación real del aire en esta prueba

Los 19 CSV no declaran la unidad ni el estadístico de agregación en sus cabeceras. Las fichas SINCA actuales no acreditan por sí solas cómo se configuró una exportación anterior. **No se asume la unidad, no se convierten ppb por intuición y no se interpreta 0000 como media diaria.**

Por eso estaciones_aire.json deja metadatos_confirmados en false y los campos pendientes en null. El lector examina los CSV y su validación, pero **no puntúa aire** hasta que cada exportación tenga evidencia de unidad compatible ug/m3, agregación diaria u horaria y huso horario. El código conserva la selección validado → preliminar etiquetado; excluye no validados. Los siete O₃ recientes están en la columna no validada. Dos CSV antiguos de MP2,5 quedan fuera del período de 2026.

En esta ejecución, los otros tres parámetros están disponibles en las ocho ubicaciones. Por tanto, todos los globales son **parciales, con 75 % de cobertura**, y cada parámetro disponible participa con un tercio del resultado. Sin dato en aire no significa contaminación alta ni baja.

## Archivos de la prueba

- probar_habita.py: ocho ubicaciones, ejecución, tabla y exportación.
- modelo.py: ocho funciones de puntuación, distancia, selección y cobertura.
- fuentes.py: lectores de Excel, SINCA, DBF y GTFS.
- plantilla.html: presentación sin dependencias web.
- test_pruebas.py: pruebas matemáticas, de lectura e integración.
- RESULTADOS.md y ejemplo_resultados/: salida de referencia de una ejecución.

## Validación

~~~powershell
py -3 -m unittest discover -s Pruebas -p "test_pruebas.py" -v
~~~

Comprueba los ejemplos del Word (78,075/100 completo y 76,75/100 sin humedad), ceros, ausencias, límites, duplicados, umbrales temporales, selección de estaciones, lectura de hojas de respaldo, consistencia entre radio y vecino, exportaciones y conservación de las fuentes. La integración exige 127 Excel, 19 CSV y el catálogo MINSAL esperado.

La aprobación de Daniel sigue pendiente. Esta carpeta permite revisar el cálculo y el acceso a los datos antes de integrarlo como versión oficial.
