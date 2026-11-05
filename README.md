# Alarmapp — MVP (Kotlin nativo, Android)

Alarma inteligente que estima tus fases de sueño con el celular y te despierta en fase ligera.
Implementa el documento **"Requisitos MVP - App de Alarma Inteligente"** (RF1–RF6, RNF1–RNF6, HU1–HU7).

## Abrir y ejecutar

1. Android Studio (Ladybug o más reciente) → **Open** → carpeta `Alarmapp`.
2. Deja que sincronice Gradle (descarga AGP 8.7.3, Kotlin 2.1.0, Compose, Room, SQLCipher).
   Si Android Studio sugiere actualizar versiones, puedes aceptar.
3. Conecta un celular Android 8.0+ (minSdk 26) y presiona **Run**.
4. Pruebas de la lógica de sueño: `./gradlew :app:testDebugUnitTest`.

> El servidor es opcional. Para activarlo, crea la tabla con `docs/supabase.sql` y agrega en `local.properties`:
> ```
> supabase.url=https://TU-PROYECTO.supabase.co
> supabase.anonKey=TU_CLAVE_ANON
> ```
> `local.properties` no se sube a GitHub. Sin estas claves, el interruptor de envío aparece desactivado.

## Arquitectura

```
core/sleep/   Lógica de sueño PURA (sin Android): fases, ciclos, ventana, aprendizaje.
              Probada con JUnit; es la parte a portar en la migración a Flutter.
monitor/      SleepDataSource (capa desacoplada) + PhoneSensorSource (acelerómetro y micrófono)
              + SleepMonitorService (servicio en primer plano durante la noche).
alarm/        AlarmScheduler (AlarmManager.setAlarmClock), AlarmService (despertar progresivo),
              AlarmActivity (amanecer en pantalla), WakeCheckActivity ("¿Sigues despierto?"),
              receptores de alarma y de reinicio, AlarmStore (estado disponible antes de desbloquear).
data/         Room + SQLCipher: Perfil, Alarma, Sesión de sueño, Indicadores, Calificación.
sync/         Envío opcional de resúmenes anónimos (WorkManager).
ui/           Jetpack Compose: inicio, "Me voy a dormir", noche, alarma, reporte, ajustes.
```

Para integrar un wearable más adelante basta con otra clase que implemente `SleepDataSource`.

## Trazabilidad con el documento

| Requisito | Dónde |
|---|---|
| RF1 Botón "Me voy a dormir" (≤3 toques, baja brillo) | `ui/home/HomeScreen.kt`, `ui/night/NightScreen.kt` |
| RF2 Monitoreo, audio solo como indicadores | `monitor/PhoneSensorSource.kt` |
| RF3 Fases ligero/profundo/REM, sin internet | `core/sleep/PhaseEstimator.kt` |
| RF4 Tres modos, ventana 30 min (10–45), hora límite obligatoria | `core/sleep/SmartWake.kt`, `CycleCalculator.kt`, `alarm/AlarmScheduler.kt` |
| RF5 Luz → sonido gradual → vibración; posponer 2×5 min | `alarm/AlarmService.kt`, `AlarmActivity.kt` |
| RF6 Reporte y calificación 1–5 | `ui/report/ReportScreen.kt` |
| Datos y privacidad (Ley 1581) | `sync/SummaryUploadWorker.kt`, `docs/supabase.sql` |
| RNF2 Suena con app cerrada, en silencio o tras reinicio | `setAlarmClock`, canal `USAGE_ALARM`, `BootReceiver` + Direct Boot |
| RNF4 Modo oscuro por defecto | `ui/theme/Theme.kt` |
| RNF5 Datos cifrados y borrables | `data/AppDatabase.kt`, Ajustes → "Borrar todos mis datos" |
| HU7 Despertar garantizado (siempre activo) | `AlarmService.escalate()`, `WakeCheckActivity`, aviso de batería |

## Colores (y por qué)

| Momento | Paleta | Uso |
|---|---|---|
| Día | **Lavanda**: fondo `#F6F3FB`, lavanda `#6E5BB8` / `#4E3F95`, durazno `#A04F30` | Inicio, alarmas, ajustes y reporte. Suave, pastel y minimalista. |
| Noche | **Arena y cielo**: azul noche `#1A1F27`, arena cálida `#E7C98F`, cielo `#8FB8DE` | Pantalla de la noche y "¿Sigues despierto?". Oscuro pero no negro, con texto cálido y brillo al mínimo para no encandilar. |
| Alarma | **Amanecer**: rojo profundo → brasa `#C2410C`, texto crema | Simula un amanecer; destellos si no hay respuesta. |

RNF4 pasa de "modo oscuro por defecto" a "claro de día, oscuro cálido durante la noche".
Todos los textos cumplen contraste ≥ 4.5:1 (WCAG AA) sobre su fondo.

## Decisiones añadidas durante el desarrollo

- **Alarma diaria de respaldo**: si guardas una hora fija y olvidas presionar "Me voy a dormir",
  la alarma igual suena (sin medición).
- **Servicio tipo `health`** además de `microphone`: en Android 14+ permite medir solo con el
  acelerómetro si el usuario no concede el micrófono. Usa `HIGH_SAMPLING_RATE_SENSORS`, permiso
  normal que no se le pide al usuario.
- **La luz sube 2 minutos antes** del sonido; el sonido arranca a la hora límite exacta.
- **"¿Sigues despierto?"** aparece 3 min después de apagar y da 60 s para responder.
- **Escalada** a los 2 min sin respuesta: volumen máximo del canal de alarma y vibración continua;
  el volumen original se restaura al apagar. Se detiene sola tras 60 min.
- La pantalla nocturna no se mantiene encendida (ahorro de batería); se puede terminar la noche
  manteniendo presionado el botón inferior.

## Limitaciones conocidas

- La estimación de fases usa reglas (etapa 1 de IA). Los umbrales son heurísticos y **no están
  validados clínicamente**; deben calibrarse con noches reales. La etapa 2 (TensorFlow Lite) queda
  preparada por la separación de `core/sleep`, pero no está implementada.
- La respiración por micrófono es débil y ruidosa: si no hay señal clara se guarda `null`, no un valor inventado.
- El consumo de batería (RNF1: ≤10–15 %) debe medirse en dispositivos reales.
- Ninguna app garantiza el 100 %: si el celular se apaga o se descarga, la alarma no puede sonar.
- En Android 14+, Play solo concede "pantalla completa" a apps de alarma declaradas; si no, la
  alarma suena igual pero aparece como notificación emergente.

## GitHub

```bash
git init && git add . && git commit -m "MVP Alarmapp en Kotlin"
git branch -M main
git remote add origin https://github.com/USUARIO/alarmapp.git
git push -u origin main
# Cuando el MVP esté estable:
git tag v1.0-kotlin && git push origin v1.0-kotlin
# Migración futura:
git checkout -b flutter-migration
```
