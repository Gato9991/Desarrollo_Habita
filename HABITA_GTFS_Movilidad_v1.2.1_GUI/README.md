# HABITA · Movilidad GTFS con GUI
**Motor 1.2.1 · Interfaz gráfica local**

Aplicación para consultar accesibilidad a transporte público en Santiago. Conserva el motor del paquete original y añade una GUI en español con identidad visual HABITA: azul oscuro, turquesa y el isotipo de la referencia entregada.

## Inicio en Windows
1. Descarga este repositorio con **Code → Download ZIP** y descomprímelo.
2. Entra a **HABITA_GTFS_Movilidad_v1.2.1_GUI**.
3. Con Python 3.10 o superior instalado, abre **INICIAR_HABITA.bat** con doble clic.
4. La primera vez, el lanzador crea un entorno virtual e instala las dependencias. Requiere internet para esa instalación.
5. Se abre el navegador en una dirección local como **http://127.0.0.1:8765**. Mantén la terminal abierta mientras usas la aplicación. Ctrl+C para detenerla.

El navegador es la interfaz; Python realiza los cálculos en este equipo. No se necesita cuenta, clave API ni alojamiento externo. Una vez instaladas las dependencias, funciona sin internet.

### Inicio manual (Windows, macOS o Linux)
Desde esta carpeta:
~~~bash
python -m venv .venv
# Windows:
.venv\Scripts\python -m pip install -r requirements.txt
.venv\Scripts\python habita_gui.py
# macOS / Linux:
# .venv/bin/python -m pip install -r requirements.txt
# .venv/bin/python habita_gui.py
~~~
Para abrirlo sin lanzar el navegador: **python habita_gui.py --no-browser**.
Si el puerto 8765 está ocupado, se busca otro libre entre los siguientes 19.
Puerto específico: **python habita_gui.py --port 9000**.

## Qué permite consultar
- Ubicaciones de ejemplo: Plaza de Armas de San Bernardo y Estadio Nacional.
- Cualquier otra ubicación por **latitud y longitud**. No incluye geocodificador de direcciones.
- Fecha de servicio dentro de la vigencia del feed.
- Paradero físico más cercano y paradero activo más cercano, hasta 1.000 m.
- Distancia geodésica y caminata teórica.
- Recorridos, sentidos, destinos, servicios e intervalos en punta mañana y tarde.
- Cobertura de 500 y 800 m, con eventos por paradero e instancias únicas de viaje.
- Metadatos, limitaciones y el JSON completo.
- Descarga de informe JSON.
- Cambio de feed por ZIP (hasta 300 MB) o ruta de carpeta/ZIP local.

La primera consulta carga el feed en memoria. Las siguientes reutilizan el motor. El análisis se ejecuta en segundo plano; la interfaz muestra el estado.

## Fuente incluida
**data/GTFS_20260829.zip** contiene los datos del paquete proporcionado, con versión declarada en feed_info.txt. La aplicación muestra su vigencia real y valida las fechas. La fecha sugerida es la fecha local del equipo si está en vigencia; si no, el extremo de vigencia más cercano. Los ejemplos guardados en resultados/ corresponden al **23/09/2026**.

El ZIP se distribuye en partes dentro de data/. Al iniciar, la aplicación lo reconstruye automáticamente y verifica su integridad mediante SHA-256. Los archivos originales GTFS se conservan sin modificar dentro del ZIP. No se descarga ni actualiza el feed automáticamente. El usuario puede reemplazarlo desde «Cambiar fuente GTFS».

## Lectura correcta
- Datos de **programación estática**: no son llegadas observadas en tiempo real.
- Distancia en línea recta, no distancia por calles.
- Caminata teórica a 1,4 m/s; no un recorrido peatonal validado.
- Puntos de parada en un radio no equivalen necesariamente a paraderos activos ese día.
- Eventos de paso y viajes únicos son conteos distintos; ninguno garantiza vehículos físicos distintos.
- El intervalo ponderado no equivale a frecuencia combinada cuando hay patrones simultáneos.
- Las horas GTFS superiores a 24:00 pertenecen al día de servicio indicado y a la madrugada del día calendario siguiente.
- Este módulo **no calcula un puntaje compuesto HABITA**.

## Estructura
- **habita_gui.py**: servidor local y puente hacia el analizador.
- **web/**: interfaz, estilos, JavaScript y referencia visual.
- **gtfs_analyzer_v1_2_1.py**: motor original sin modificaciones.
- **data/GTFS_20260829.zip**: feed del paquete original.
- **resultados/**: informes originales de ejemplo.
- **test_gtfs_analyzer_v1_2_1.py**: 38 pruebas del motor.
- **test_gui.py**: validaciones y prueba integrada de GUI/API.
- **README_v1_2_1.md**, **GTFS_ANALYZER_GUIDE_v1_2_1.md** y **CHANGELOG_v1_2_1.md**: documentación original.

### Referencia gráfica
El isotipo HABITA se muestra a partir de la imagen proporcionada por el equipo, mediante un recorte visual CSS; no fue redibujado. La lámina completa se guarda como recurso de referencia en web/assets/referencia_visual.png. La GUI no incorpora como encabezados los logos universitarios ni de Jump Chile.

### Pruebas
~~~bash
python -m pip install pytest
python -m pytest -q
~~~

### Uso local
El servidor escucha solo en **127.0.0.1**, valida Host/Origin y exige un token de sesión para las operaciones de escritura. No es un servidor multiusuario ni un despliegue de producción.
Los ZIP seleccionados se guardan localmente en uploads/; esa carpeta está excluida del repositorio.

HABITA, proyecto fundado por Mauricio Medina, con participación de Daniel Morales, Cristian Morales y Jordan Navia en su etapa de desarrollo.
