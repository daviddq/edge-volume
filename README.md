# Edge Volume

App Android mínima (sin Gradle) que sustituye el botón físico de volumen roto de un Poco X3 Pro (MIUI 13, Android 12).

Un servicio de accesibilidad dibuja una franja invisible (`TYPE_ACCESSIBILITY_OVERLAY`) en el borde derecho de la pantalla. Funciona encima de la pantalla de bloqueo (sin desbloquear) y dentro de apps o juegos en pantalla completa.

## Gestos

| Gesto sobre la franja | Acción |
|---|---|
| Deslizar hacia arriba / abajo | Subir / bajar volumen multimedia (o de llamada si hay una en curso). Un paso cada ~14 dp (vertical) o ~9 dp (horizontal). |
| Doble toque rápido | Captura de pantalla (`GLOBAL_ACTION_TAKE_SCREENSHOT`) |

## Posición de la franja

La franja evita el tirador del gesto "atrás" de MIUI (`GestureStubRight`), que roba los toques aunque haya rectángulos de exclusión.

| Orientación | Franja (y) | Zona "atrás" de MIUI (y) |
|---|---|---|
| Vertical (2400 px) | 20 %–36,5 % (480–876) | 37 %–97 % (892–2332) |
| Horizontal (1080 px) | 0 %–19 % (0–205) | 20 %–80 % (216–864) |

Ancho: 22 dp. Las constantes están al principio de `EdgeVolumeService.java` y en `applyGeometry()`.

## Compilar e instalar

Requisitos: Android SDK con `build-tools/36.0.0` y `platforms/android-36` (por defecto en `/opt/homebrew/share/android-commandlinetools`, o usa `ANDROID_HOME`) y un JDK 11 o posterior.

```bash
./build.sh
adb install -r EdgeVolume.apk
```

La primera compilación genera `debug.keystore`. Consérvalo: si se pierde, hay que desinstalar la app antes de instalar una versión nueva.

## Configuración en el teléfono (una sola vez)

```bash
P=dev.local.edgevolume
adb shell settings put secure enabled_accessibility_services $P/.EdgeVolumeService
adb shell settings put secure accessibility_enabled 1
adb shell dumpsys deviceidle whitelist +$P
adb shell cmd appops set $P RUN_ANY_IN_BACKGROUND allow
# Permisos propios de MIUI: inicio automático, pantalla de bloqueo, ventanas emergentes en segundo plano
for op in 10008 10020 10021; do adb shell appops set $P $op 0; done
```

Para escribir ajustes `secure` por adb, MIUI necesita activar *Depuración USB (Ajustes de seguridad)*. También se puede activar a mano en *Ajustes → Ajustes adicionales → Accesibilidad → Edge Volume*.

## Verificación rápida

```bash
adb shell cmd media_session volume --stream 3 --get
adb shell input swipe 1060 820 1060 700 300   # sube 3 pasos (vertical)
adb shell dumpsys accessibility | grep "Bound services"
```

## Licencia

[MIT](LICENSE)
