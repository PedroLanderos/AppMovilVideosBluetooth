# BluetoothVideoPlayerStarter

LINK CON VIDEO DE PRUEBA: https://drive.google.com/file/d/15GiRz4ZVAf57E3kxM6z6Xn52OC0N0luF/view?usp=sharing

Aplicación Android nativa en Kotlin + XML que implementa una arquitectura Cliente-Servidor por Bluetooth para buscar, transferir y reproducir videos en un dispositivo cliente sin conexión directa a Internet.

## Modos principales

- **Servidor**: dispositivo con Internet y/o videos disponibles. Atiende búsquedas, prepara videos y los transfiere por Bluetooth.
- **Cliente**: dispositivo sin Wi‑Fi ni datos móviles. Busca videos, recibe resultados por Bluetooth, descarga el video por chunks y lo reproduce localmente.

## Fuentes de video

### Biblioteca del servidor

El servidor puede agregar videos directamente desde el almacenamiento del celular mediante el selector de archivos de Android.

Flujo:

1. En el dispositivo servidor, abrir la pantalla **Servidor**.
2. Presionar **Agregar videos desde este celular**.
3. Elegir uno o varios archivos de video.
4. El servidor guarda una referencia persistente al archivo seleccionado.
5. El cliente busca en **Biblioteca servidor**.
6. El servidor responde con los videos que coinciden con la búsqueda.
7. El cliente selecciona un resultado.
8. El servidor transfiere el video por Bluetooth.
9. El cliente lo reproduce sin Internet.

Este modo es el recomendado para la demo porque no requiere recompilar la app cada vez que se quieran cambiar los videos.

### Videos demo internos

Además de la biblioteca del servidor, el proyecto mantiene soporte para videos incluidos en:

```text
app/src/main/assets/videos/
```

El catálogo demo se configura en:

```text
app/src/main/assets/catalog.json
```

El `id` del catálogo debe coincidir con el nombre del archivo MP4.

Ejemplo:

```json
{
  "id": "sample_low",
  "title": "Video demo",
  "source": "Biblioteca local",
  "verified": true,
  "durationText": "00:30"
}
```

Archivo esperado:

```text
app/src/main/assets/videos/sample_low.mp4
```

### YouTube

El cliente puede seleccionar **YouTube** para mandar una búsqueda al servidor. El servidor usa `yt-dlp` mediante `youtubedl-android`, intenta descargar/cachear el video y después lo envía por Bluetooth al cliente.

Este modo depende de Internet en el servidor, disponibilidad del contenido, tamaño del video y comportamiento de YouTube.

## Funcionalidades implementadas

- Bluetooth Classic por RFCOMM.
- Protocolo por frames binarios.
- Búsqueda desde cliente sin Internet.
- Biblioteca local seleccionable desde el servidor.
- Videos demo internos en assets.
- Modo YouTube.
- Transferencia de video por chunks.
- Indicador de buffer, progreso y velocidad aproximada.
- Reproducción local con `VideoView` y `MediaController`.
- Historial persistente con SQLite.
- Favoritos persistentes.
- Modo privado para no guardar historial.
- Modo bajo consumo.
- Temas Guinda IPN y Azul ESCOM, con modo claro/oscuro/sistema.

## Prueba recomendada

1. Instalar la app en dos celulares Android físicos.
2. Emparejar ambos celulares desde Ajustes de Bluetooth.
3. En el celular A, abrir la app como **Servidor**.
4. En el servidor, agregar videos con **Agregar videos desde este celular**.
5. Presionar **Iniciar servidor Bluetooth**.
6. En el celular B, apagar Wi‑Fi y datos móviles.
7. Abrir la app como **Cliente**.
8. Conectar al dispositivo servidor emparejado.
9. Seleccionar **Biblioteca servidor**.
10. Buscar un video por nombre.
11. Seleccionar **Reproducir**.
12. Esperar la transferencia y validar la reproducción.

## Recomendaciones de video

Para pruebas iniciales:

```text
MP4
H.264
AAC
360p o 480p
10 segundos a 3 minutos
1 MB a 40 MB
```

Evitar al inicio:

```text
1080p o 4K
60 FPS
videos mayores a 100 MB
videos de más de 5 minutos
```

## Archivos principales

```text
app/src/main/java/com/example/btvideo/ui/ServerActivity.kt
app/src/main/java/com/example/btvideo/ui/ClientActivity.kt
app/src/main/java/com/example/btvideo/data/ServerVideoLibrary.kt
app/src/main/java/com/example/btvideo/data/VideoCatalog.kt
app/src/main/java/com/example/btvideo/data/YoutubeExperimentalSource.kt
app/src/main/java/com/example/btvideo/bluetooth/BluetoothConnection.kt
app/src/main/java/com/example/btvideo/bluetooth/Protocol.kt
```

## Notas

- El emulador sirve para revisar la interfaz, pero la comunicación Bluetooth real debe probarse con dos dispositivos físicos.
- El servidor debe tener Internet únicamente si se usa YouTube.
- El cliente debe permanecer sin Wi‑Fi ni datos móviles durante la prueba.

## Tests instrumentados para YouTube

Se agregó un test instrumentado en:

```text
app/src/androidTest/java/com/example/btvideo/data/YoutubeExperimentalSourceTest.kt
```

Este test valida:

- Que la búsqueda de YouTube regrese resultados.
- Que `YoutubeExperimentalSource.getVideo(...)` genere un `TransferVideo` con MIME `video/mp4`.
- Que el archivo resultante tenga una pista de video detectable con `MediaMetadataRetriever`.
- Que el modo normal y el modo bajo consumo puedan producir un MP4 reproducible.

Para ejecutarlo:

1. Conecta un celular físico o abre un emulador con Internet.
2. En Android Studio abre `YoutubeExperimentalSourceTest.kt`.
3. Presiona el botón verde junto a la clase o junto a cada test.
4. Ejecuta `Run 'YoutubeExperimentalSourceTest'`.

Nota: estos tests dependen de Internet, YouTube, yt-dlp y FFmpeg. Si YouTube cambia formatos o bloquea temporalmente el acceso a un video, el test puede fallar aunque la app compile correctamente.


## Historial y favoritos visibles

En la pantalla Cliente hay dos botones: **Ver historial** y **Ver favoritos**.

- **Ver historial** muestra los videos reproducidos cuando el modo privado está apagado.
- **Ver favoritos** muestra los videos marcados con el botón Favorito.
- Desde ambas listas se puede volver a reproducir el video.
- En Favoritos se puede quitar un elemento con el botón Quitar favorito.
- En Historial se puede limpiar la lista con el botón Limpiar historial.

Los tests de almacenamiento están en:

```text
app/src/androidTest/java/com/example/btvideo/data/LocalStoreTest.kt
```

Para probarlos desde Android Studio, abre ese archivo y ejecuta la clase `LocalStoreTest`.
