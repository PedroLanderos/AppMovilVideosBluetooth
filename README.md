# BluetoothVideoPlayerStarter

Aplicación móvil Android nativa desarrollada en **Kotlin + XML** que implementa una arquitectura **Cliente-Servidor por Bluetooth** para buscar, transferir y reproducir videos en un dispositivo sin conexión a Internet.

La aplicación está pensada para funcionar con dos dispositivos Android físicos:

- **Dispositivo A / Servidor**: tiene conexión a Internet y atiende las solicitudes del cliente.
- **Dispositivo B / Cliente**: no tiene Internet y se comunica únicamente por Bluetooth.

---

## Funcionalidades implementadas

- Modo **Servidor**.
- Modo **Cliente**.
- Comunicación por **Bluetooth Classic / RFCOMM**.
- Protocolo de comunicación mediante frames binarios.
- Búsqueda de videos desde el cliente.
- Selector de fuente de video:
  - **Local**: busca videos autorizados dentro de `assets/catalog.json` y transfiere archivos MP4 desde `assets/videos/`.
  - **YouTube experimental**: el servidor usa Internet para buscar/descargar contenido mediante `yt-dlp` usando `youtubedl-android`, lo guarda en caché y lo envía al cliente por Bluetooth.
- Transferencia de video por chunks.
- Indicador de progreso de transferencia.
- Cálculo aproximado de velocidad de transferencia.
- Buffering antes de reproducir.
- Reproducción local en el cliente usando `VideoView` y `MediaController`.
- Controles básicos de reproducción:
  - Reproducir.
  - Pausar.
  - Adelantar.
  - Retroceder.
  - Barra de progreso.
- Historial persistente con SQLite.
- Favoritos persistentes.
- Modo privado para no guardar videos en el historial.
- Modo bajo consumo para intentar usar versiones de menor calidad.
- Indicadores visuales del estado de conexión Bluetooth.
- Temas visuales:
  - **Tema Guinda IPN**.
  - **Tema Azul ESCOM**.
- Compatibilidad con modo claro, oscuro y modo del sistema.

---

## Estructura general del proyecto

```text
app/
 └── src/
     └── main/
         ├── java/com/example/btvideo/
         │   ├── BtVideoApplication.kt
         │   ├── bluetooth/
         │   ├── data/
         │   ├── model/
         │   ├── ui/
         │   └── util/
         │
         ├── res/
         │   ├── layout/
         │   │   ├── activity_main.xml
         │   │   ├── activity_client.xml
         │   │   └── activity_server.xml
         │   │
         │   ├── values/
         │   │   ├── colors.xml
         │   │   ├── themes.xml
         │   │   └── attrs.xml
         │   │
         │   └── values-night/
         │       └── themes.xml
         │
         └── assets/
             ├── catalog.json
             └── videos/