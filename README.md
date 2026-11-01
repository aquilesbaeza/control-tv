# Control TV

Control remoto para la Xiaomi (Android TV) desde el Galaxy Z Fold 7.
Se conecta por Wi-Fi con ADB (puerto 5555), igual que los scripts de cleanyourtv.

- **Control**: flechas, OK (mantener = OK largo), atrás, inicio, ajustes, volumen, silencio, apagar, entradas, play/pausa.
- **Touchpad**: deslizá para moverte, tocá para OK.
- **Apps**: lista las apps de la TV y las abre con un toque.
- **Teclado**: escribís en el cel y aparece en la TV.
- Los botones de volumen del celular controlan la TV.
- Fold abierto: dos columnas. Pantalla de portada: pestañas abajo.

## Compilar
Cada `git push` a `main` compila la APK con GitHub Actions y la publica en **Releases**.
Local (Windows, con JDK 17 y el SDK de Android): `.\gradlew.bat assembleDebug`
→ `app\build\outputs\apk\debug\app-debug.apk`

## Primera vez
Al abrir la app, la TV muestra "¿Permitir depuración?". Marcá **Permitir siempre** y OK.
La IP se cambia con el engranaje (por defecto 192.168.100.29).
