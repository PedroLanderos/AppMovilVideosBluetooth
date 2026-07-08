# BluetoothVideoPlayerStarter

Proyecto base en Android nativo + Kotlin + XML para una app cliente-servidor por Bluetooth.

## Qué implementa

- Modo Dispositivo A / Servidor.
- Modo Dispositivo B / Cliente.
- Conexión Bluetooth Classic por RFCOMM.
- Protocolo por frames binarios.
- Búsqueda en un catálogo local del servidor (`assets/catalog.json`).
- Transferencia de video MP4 del servidor al cliente por chunks.
- Buffer/progreso/velocidad aproximada.
- Reproducción local en el cliente con `VideoView` y `MediaController`.
- Historial y favoritos persistentes con SQLite.
- Modo privado para no guardar historial.
- Tema Guinda y Azul con versión light/night.

## Importante sobre YouTube

Este starter no extrae ni descarga streams de YouTube. Las políticas de YouTube prohíben descargar, cachear, almacenar copias del contenido audiovisual o hacerlo disponible offline sin aprobación previa. Para una demo segura, usa videos propios, autorizados, de dominio público o un backend/servidor de video que tengas derecho a retransmitir.

Puedes usar YouTube Data API solo para búsqueda/metadata en el servidor si tu docente lo permite, pero no para reenviar el audiovisual por Bluetooth.

## Cómo probar

1. Abre el proyecto en Android Studio.
2. Agrega videos MP4 autorizados en:
   `app/src/main/assets/videos/`
3. Asegúrate de que `catalog.json` tenga el mismo id que el archivo. Ejemplo:
   `id = sample_low` => `videos/sample_low.mp4`.
4. Instala la app en dos celulares Android físicos.
5. Empareja ambos celulares desde Ajustes de Bluetooth.
6. En el celular con Internet, abre modo Servidor e inicia el servidor.
7. En el celular sin Internet, abre modo Cliente, elige el dispositivo emparejado y conecta.
8. Busca `sample` y reproduce.

## Pendientes recomendados para subir calificación

- Reconexión automática con reintentos.
- Transferencia con Range real para seek antes de descargar completo.
- Servicio foreground para conexión larga.
- Catálogo remoto usando YouTube Data API para metadata, sin descargar video.
- Codificación previa de los videos a 144p/360p para mejor fluidez por Bluetooth.
