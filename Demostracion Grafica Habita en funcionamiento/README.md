# Demostracion Grafica Habita en funcionamiento

[Descargar demostración offline](./Demostracion%20Grafica%20Habita%20en%20funcionamiento.zip)

## Cómo abrirla

1. Descarga el ZIP y descomprímelo.
2. Abre `HABITA_Offline/index.html` con un navegador.
3. Selecciona uno de los seis puntos para consultar su puntaje, la descripción automática y los componentes del cálculo.

Esta demostración funciona sin internet, sin instalar programas y sin una cuenta de ChatGPT.

## Alcance y estado del prototipo

Este trabajo demuestra cómo podría funcionar la página de HABITA: el usuario selecciona una ubicación y recibe información sobre su entorno, junto con un puntaje calculado mediante el Índice HABITA.

**El índice, las fórmulas y las ponderaciones son provisionales. Todos los valores utilizados son datos de prueba simulados; bajo ninguna circunstancia deben presentarse como mediciones reales ni como una evaluación validada de las ubicaciones.** Los seis puntos son identificadores de demostración y no representan direcciones reales.

Su finalidad es probar el recorrido del usuario, el funcionamiento del cálculo y la presentación de la información que explica el puntaje. Tanto los datos como la metodología podrán actualizarse a medida que se incorporen fuentes verificadas y se valide el modelo.

## Funcionamiento previsto para una próxima versión

La lista desplegable será reemplazada por un campo donde el usuario podrá ingresar la dirección o el nombre del lugar que desea consultar.

Se prevé integrar un servicio de Google Maps Platform para resolver la ubicación. En particular, la **Geocoding API** permite convertir una dirección en coordenadas geográficas —latitud y longitud—. La búsqueda por nombre de lugar se concretará con el servicio apropiado y la confirmación de la ubicación cuando existan coincidencias ambiguas.

A partir de la ubicación resuelta, algunos indicadores se calcularán para ese punto o su entorno cercano —por ejemplo, distancia y cantidad de paraderos o establecimientos de salud—. Otros utilizarán información general de la comuna correspondiente, cuando esa sea la cobertura disponible de la fuente. La aplicación deberá distinguir ambos niveles.

La API de ubicación permitirá localizar el punto; los indicadores ambientales y territoriales procederán de sus fuentes correspondientes. **Esta integración es una etapa futura y todavía no está implementada en el ZIP.** La demostración actual conserva sus datos precargados y su funcionamiento sin conexión; la futura consulta a servicios externos requerirá conexión a internet.

Referencia técnica: [Geocoding API de Google Maps Platform](https://developers.google.com/maps/documentation/geocoding/overview).
