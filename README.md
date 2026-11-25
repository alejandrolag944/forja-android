# Forja

**Forja** es una app Android de código abierto para ver los FPS de una app y las temperaturas CPU/SoC y GPU que el teléfono exponga. Tiene una interfaz oscura con tonos cálidos, historial visual y frecuencia de lectura ajustable.

> Hecha con asistencia de IA (OpenAI Codex). El código queda abierto para que cualquiera lo revise, aprenda de él y lo mejore.

## Qué incluye

- FPS calculados a partir de los tiempos de presentación de `SurfaceFlinger` para la app seleccionada.
- Lectura de las zonas térmicas que el kernel del teléfono permita consultar; las temperaturas CPU/SoC y GPU se clasifican por el nombre real de cada sensor.
- Conexión mediante Shizuku, que puede iniciarse con depuración inalámbrica en Android 11 o superior, y acceso root opcional.
- Intervalos seleccionables de 0,5, 1, 2 y 5 segundos.
- Sesión de medición en segundo plano con notificación persistente y ventana flotante opcional para ver los valores encima de un juego.
- Sin cuenta, servidor, telemetría ni API de IA durante el uso de la app.

## Compatibilidad y lecturas

Android y los fabricantes no ofrecen una API pública y universal para leer las temperaturas de CPU/GPU ni los FPS de otra app. Por eso Forja solicita acceso local mediante Shizuku o root y consulta las interfaces de solo lectura de `SurfaceFlinger` y `/sys/class/thermal`.

La disponibilidad depende del dispositivo, su versión de Android, el kernel y el nivel con que se inició Shizuku. Algunos teléfonos ocultan la temperatura de GPU, nombran los sensores de forma distinta o no entregan tiempos de fotogramas. En esos casos Forja muestra **-- / Sensor no expuesto** o espera fotogramas; no sustituye el dato por una estimación. El valor CPU/SoC indica el tipo de sensor que el propio kernel publica.

La lectura de FPS necesita que la capa gráfica de la app exista cuando se consulta. En ciertos juegos o ROMs, `SurfaceFlinger --latency` está limitado y el FPS aparecerá como no disponible aunque Shizuku esté conectado.

La ventana flotante requiere que autorices **Mostrar sobre otras apps** en Android. La notificación persistente aparece mientras la sesión está activa y tiene un botón para detenerla.

## Activación

### Shizuku por depuración inalámbrica (Android 11+)

1. Instala Shizuku desde su [sitio oficial](https://shizuku.rikka.app/).
2. Activa las Opciones de desarrollador y **Depuración inalámbrica**.
3. En Shizuku, inicia el servicio con el flujo de emparejamiento inalámbrico que muestra la app.
4. Vuelve a Forja, pulsa **Conectar Shizuku** y autoriza el servicio.
5. Elige un juego, pulsa **Empezar a medir** y permite la ventana flotante si quieres ver las cifras sobre el juego.

Después de reiniciar el teléfono, puede ser necesario iniciar Shizuku otra vez. Shizuku también puede arrancarse por ADB desde un ordenador.

### Root

Pulsa **Probar** en la sección root y acepta el permiso desde el gestor de root. Forja ejecuta únicamente las lecturas de sensores y `SurfaceFlinger` descritas en el código.

Después conecta un juego y comienza la sesión igual que con Shizuku. La guía completa también aparece dentro de la app en **Guía paso a paso**.

## Compilar

Abre esta carpeta en Android Studio con JDK 17 y Android SDK API 36. Android Studio puede sincronizar las dependencias del proyecto.

En Windows, desde esta carpeta:

```powershell
.
.\gradlew.bat assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Tecnología

- Kotlin y Jetpack Compose.
- Android Gradle Plugin 8.13.2, Kotlin 2.2.21.
- [Shizuku API](https://github.com/RikkaApps/Shizuku-API) para el servicio local con identidad shell/root.

## Privacidad

Todo se procesa en el dispositivo. Forja no recopila ni envía mediciones. La lista de apps se usa solo para que puedas escoger un objetivo; no se sube a ningún servicio.

## Código abierto y ayuda de IA

Licencia MIT. Este proyecto fue creado con asistencia de IA y puede contener errores o necesitar adaptaciones para modelos concretos de teléfono. Las métricas, los comandos y las limitaciones están disponibles para revisión en el código fuente. [CodeRabbit](https://coderabbit.ai/) está configurado para revisar pull requests cuando su app esté habilitada en el repositorio.

Las contribuciones son bienvenidas; consulta [CONTRIBUTING.md](CONTRIBUTING.md).
