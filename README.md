# BluetoothVideoPlayerStarter

Proyecto base en Android nativo + Kotlin + XML para una app cliente-servidor por Bluetooth.

## Qué implementa ahora

- Modo Dispositivo A / Servidor.
- Modo Dispositivo B / Cliente.
- Conexión Bluetooth Classic por RFCOMM.
- Protocolo por frames binarios.
- Selector de fuente en el cliente:
  - **Local**: busca en `assets/catalog.json` y transfiere MP4 autorizados desde `assets/videos/`.
  - **YouTube experimental**: el servidor busca/descarga usando `yt-dlp` mediante `youtubedl-android` y después manda el archivo al cliente por Bluetooth.
- Transferencia de video por chunks.
- Buffer/progreso/velocidad aproximada.
- Reproducción local en el cliente con `VideoView` y `MediaController`.
- Historial y favoritos persistentes con SQLite.
- Modo privado para no guardar historial.
- Tema Guinda y Azul con versión light/night.

## Importante sobre YouTube

El modo YouTube experimental usa una dependencia externa basada en `yt-dlp`. Úsalo solo con contenido propio, autorizado o cuando tengas permiso explícito para descargar/retransmitir el contenido.

Para una entrega escolar, conviene presentar dos caminos:

1. **Modo Local / Demo segura**: demuestra la transferencia y reproducción real sin Internet en el cliente.
2. **Modo YouTube experimental**: demuestra búsqueda/descarga desde el servidor con Internet y envío por Bluetooth, aclarando que depende de una librería externa y de autorización sobre el contenido.

También toma en cuenta que tu consigna menciona que no se deben usar frameworks de terceros. `youtubedl-android` es una librería/wrapper externo, así que conviene confirmarlo con el docente.

## Cómo probar modo Local

1. Abre el proyecto en Android Studio.
2. Agrega videos MP4 autorizados en:
   `app/src/main/assets/videos/`
3. Asegúrate de que `catalog.json` tenga el mismo id que el archivo. Ejemplo:
   `id = sample_low` => `videos/sample_low.mp4`.
4. Instala la app en dos celulares Android físicos.
5. Empareja ambos celulares desde Ajustes de Bluetooth.
6. En el celular con Internet, abre modo Servidor e inicia el servidor.
7. En el celular sin Internet, abre modo Cliente, elige el dispositivo emparejado y conecta.
8. Selecciona **Local**, busca `sample` y reproduce.

## Cómo probar modo YouTube experimental

1. Usa un celular real como servidor. El emulador puede fallar por binarios nativos.
2. El servidor debe tener Internet.
3. El cliente debe estar sin Internet, conectado únicamente por Bluetooth.
4. En el cliente selecciona **YouTube experimental**.
5. Busca por palabras clave o pega una URL.
6. Selecciona un resultado y presiona **Reproducir**.
7. El servidor descargará/cacheará el video y lo enviará por Bluetooth al cliente.

## Nota de rendimiento

Bluetooth Classic tiene ancho de banda limitado. Para demo, usa videos cortos y baja calidad. El modo bajo consumo intenta descargar versiones de menor resolución.

## Pendientes recomendados para subir calificación

- Reconexión automática con reintentos.
- Transferencia con Range real para seek antes de descargar completo.
- Servicio foreground para conexión larga.
- Mejor UI de historial/favoritos.
- Indicadores visuales más detallados de conexión Bluetooth.
- Compresión/transcodificación previa de videos largos a 144p/240p/360p.
