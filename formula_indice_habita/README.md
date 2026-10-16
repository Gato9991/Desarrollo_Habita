# Fórmula provisional del Índice HABITA

**Versión 0.1.0-provisional. Esta metodología puede cambiar.** Los pesos, límites, referencias y reglas de combinación son supuestos de demostración pendientes de validación. No son umbrales oficiales, criterios sanitarios ni una certificación de habitabilidad o seguridad. Los datos de los ejemplos son simulados y no deben presentarse como mediciones reales.

## Uso

Requiere Python 3.10 o posterior. No necesita internet, paquetes externos, API ni servidor.

Ejecutar el ejemplo incorporado:

```bash
python formula_indice_habita.py
```

Usarlo desde otro programa situado en la misma carpeta:

```python
from formula_indice_habita import calcular_indice_habita

resultado = calcular_indice_habita(
    mp25=35, ozono=60, temperatura=25, humedad=45,
    distancia_paradero_m=150, paraderos_500m=8,
    distancia_salud_m=800, establecimientos_2km=3,
    remociones=20, fallas=30, volcanicas=0, inundaciones=40,
)
print(round(resultado["indice_habita"], 1))  # 78.0
print(resultado["cobertura_porcentaje"])     # 100.0
```

Todos los argumentos son opcionales y se entregan por nombre. Para un dato ausente, usa `None` u omite el argumento. Si no hay ningún dato, `indice_habita` es `None` y el estado es `sin_datos`. En JSON, `None` se representa como `null`.

También existen funciones independientes: `calcular_calidad_aire`, `calcular_confort_termico`, `calcular_accesibilidad_paraderos`, `calcular_accesibilidad_salud` y `calcular_amenazas_naturales`. Devuelven puntaje, cobertura y componentes. Las funciones `puntaje_*` convierten cada medición por separado.

## Variables y pesos

Un puntaje mayor representa condiciones más favorables según este modelo. Cada variable tiene un peso base de 20%.

| Variable | Componentes | Pesos internos |
|---|---|---|
| Calidad del aire | MP2,5 / O₃ | 70% / 30% |
| Confort térmico | Temperatura / humedad relativa | 60% / 40% |
| Accesibilidad a paraderos | Distancia al más cercano / cantidad en 500 m | 60% / 40% |
| Accesibilidad a salud | Distancia al más cercano / cantidad en 2 km | 70% / 30% |
| Amenazas naturales | Remociones / fallas / volcánicas / inundaciones | 25% cada una |

## Conversión a puntajes

Todos los resultados se limitan al intervalo 0–100.

| Medición de entrada | Fórmula |
|---|---|
| MP2,5 en µg/m³ | 100 × (1 − MP2,5 / 100) |
| O₃ en µg/m³ | 100 × (1 − O₃ / 200) |
| Temperatura en °C | 100 × (1 − abs(temperatura − 22) / 18) |
| Humedad relativa en % | 100 × (1 − abs(humedad − 50) / 50) |
| Distancia al paradero más cercano en m | 100 × (1 − distancia / 1000) |
| Cantidad de paraderos en 500 m | mínimo(100, cantidad × 10) |
| Distancia al establecimiento de salud más cercano en m | 100 × (1 − distancia / 5000) |
| Cantidad de establecimientos de salud en 2 km | mínimo(100, cantidad × 20) |
| Cada amenaza, exposición simulada 0–100 | 100 − exposición |

Los valores 22 °C y 50% de humedad son referencias de simulación, no una definición universal de confort. La exposición 0–100 es una escala ficticia: una fuente real puede requerir convertir categorías o polígonos mediante otra regla. El promedio de amenazas puede ocultar un componente desfavorable y no permite declarar seguro un lugar.

Las distancias al elemento más cercano son independientes del radio de conteo: ese elemento puede estar fuera del radio. Los radios deben mantenerse iguales al comparar puntos. Antes de usar datos reales deberán definirse períodos compatibles, unidades, calidad de las fuentes y representatividad territorial.

## Datos faltantes y cobertura

Para una variable i:

```text
c_i = suma de los pesos internos de los componentes disponibles
S_i = suma(peso_interno × puntaje_disponible) / c_i
```

Si c_i es cero, su puntaje es None y se excluye del índice.

Para el resultado global:

```text
peso_efectivo_i = 0.20 × c_i
cobertura_global = suma(peso_efectivo_i)
HABITA = suma(peso_efectivo_i × S_i) / cobertura_global
participacion_final_i = peso_efectivo_i / cobertura_global
```

Ejemplo: falta humedad, pero todas las demás mediciones están disponibles. Confort tiene cobertura de 60% y peso efectivo de 12%; la cobertura global es 92%. **Su participación final en el promedio reajustado es 12/92 = 13,04%, no 12%.** El módulo devuelve ambos valores para evitar confusiones.

Un cero sí cuenta como dato: cero establecimientos no equivale a cantidad desconocida. La cobertura mide disponibilidad ponderada, no certeza, validez ni número bruto de registros. Dos índices con cobertura diferente pueden reflejar conjuntos de información distintos.

## Salida y validación de entradas

La salida incluye índice, cobertura, estado, versión de fórmula, advertencia y el desglose de las cinco variables. Cada variable informa componentes, pesos internos, peso base, peso efectivo, participación final y aporte al índice.

Se rechazan con `ValueError` los textos, booleanos, NaN, infinitos, distancias o concentraciones negativas, cantidades fraccionarias, humedad fuera de 0–100 y exposiciones fuera de 0–100. Los valores válidos que superan los límites de demostración producen puntajes limitados, sin alterar la medición original.

Los cálculos conservan la precisión intermedia: redondea solo al presentar el resultado. `completo` significa que todos los componentes están disponibles; la metodología continúa siendo provisional.

## Aplicación por ubicación

Este módulo calcula a partir de las mediciones recibidas; no determina comunas ni consulta fuentes. El programa que lo invoque debe elegir los datos de cada ubicación. En el prototipo, aire y confort pueden heredarse de valores asignados a la comuna, mientras que accesibilidad y amenazas pueden ser específicos del punto. Esa asignación no demuestra que una estación represente a toda una comuna.

## Cambios futuros

Las fórmulas, ponderaciones, radios, referencias y reglas para datos faltantes podrán modificarse según la disponibilidad de información y la validación del modelo. Cada cambio metodológico deberá actualizar `VERSION_FORMULA` y recalcular los puntos comparados con la misma versión. Sustituir datos simulados por datos reales no valida automáticamente esta metodología.

## Verificación

```bash
python -m unittest -v
```

Las pruebas comprueban los seis resultados de demostración, las coberturas, los datos faltantes, la distinción entre cero y ausencia, los límites y las entradas inválidas.
