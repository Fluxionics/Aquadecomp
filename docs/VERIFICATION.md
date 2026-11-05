# Verificación de Aquadecomp 1.0.0

Este documento dice cómo comprobar, paso por paso, que la versión 1.0.0 cumple
los criterios de terminación del proyecto. Todo lo que sigue es reproducible
desde un clon limpio con Java 21 y Gradle 9.

## Comandos base

```bash
gradle test          # suite JUnit 5
gradle jar           # genera build/libs/aquedecomp.jar
```

La suite no depende de archivos externos: los fixtures de APK, AAB, XAPK y APKS
se construyen en memoria o en directorios temporales durante la ejecución.

Para regenerar los fixtures de formatos usados en las corridas manuales hace
falta un APK pequeño de origen (cualquier APK descargable sirve):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\make-fixtures.ps1 -SourceApk testdata\mini.apk
```

El script crea `mini.xapk`, `mini.apks` y `mini.aab` en `testdata/fixtures/`.

## Matriz de criterios

| # | Criterio | Dónde se comprueba | Evidencia |
|---|----------|--------------------|-----------|
| 1 | Procesa APK, AAB, XAPK y APKS | `InputNormalizerTest`, `ApkInspectorTest`, corridas de los 4 formatos | Los cuatro formatos terminan con `success: true`, `inputType` correcto y resultados idénticos entre sí (tabla más abajo) |
| 2 | Cuatro motores reales con fallback comprobado | `DecompilerStrategyTest` (CFR, Procyon, Vineflower rescatan una clase compilada en el test) + corridas con rescate en `sources/` | `gradle test` prueba cada motor con una clase conocida; en corridas reales: 65 archivos rescatados por CFR/Procyon (medium/xapk/apks/aab) y 3 por CFR (large) |
| 3 | Identifica el motor de cada archivo | `ConfidenceScorerTest`, `ReportWriterTest` | `aquedecomp-report.json` incluye `engineByFile` con una entrada por archivo `.java` (6425/6425 en la corrida mediana) y `confidence.decompilerStats` con el resumen; cada archivo rescatado existe en `sources/` |
| 4 | Confianza sin puntuaciones engañosas | `ConfidenceScorerTest.engineIdentityDoesNotChangeTheScore`, `EffectivenessCalculatorTest`, `ConfidenceLevelTest` | El motor que produjo un archivo es metadato y no altera el puntaje; la metodología y sus limitaciones van escritas en cada informe |
| 5 | Pruebas con apps pequeñas, medianas y grandes | Tabla de corridas más abajo | Tres APKs reales (50 KB, 4.6 MB, 109.7 MB), mismo comando, 3/3 con `success: true` |
| 6 | Informes correctos y APKs problemáticos no lo rompen | `PipelineFailureTest`, `ReportWriterTest`, `InputNormalizerTest`, corridas con entradas rotas | 5/5 entradas inválidas terminan con código de salida 1, mensaje en español y `aquedecomp-report.json` con `success: false` |
| 7 | Documentación clara y resultados reproducibles | Este archivo, `README.md`, `docs/`, `gradle test` (88 pruebas) | Comandos exactos, fixtures regenerables, tabla de resultados y corridas repetidas con el mismo JAR |

## Reproducibilidad

La matriz completa se ejecutó dos veces con el mismo JAR de 1.0.0 y los
resultados coincidieron en efectividad, número de archivos y distribución de
motores para los seis casos (los tiempos varían por carga de la máquina). La
única diferencia frente a una corrida previa de desarrollo fue el AAB, que
pasó de 85.0 a 91.0 de efectividad y de paquete vacío a `com.example.mini`
cuando se corrigió la búsqueda del manifiesto decodificado.

## Entradas problemáticas

Caso de uso: cualquier archivo que no sea un paquete Android válido debe
rechazarse sin traza de pila y dejando informe de error.

```bash
java -jar build/libs/aquedecomp.jar decompile entrada-invalida.apk -o salida
echo $?    # 1
```

Comportamiento verificado para: archivo de 0 bytes, texto renombrado a `.apk`,
ZIP truncado, ZIP sin contenido Android y archivo inexistente (**5/5 casos**):

- código de salida `1`
- mensaje `Falló: ...` en español, sin traza de pila
- `salida/aquedecomp-report.json` con `"success": false` y `errorMessage`

Casos observados: `No es un archivo ZIP válido: ...` (vacío y texto),
`zip END header not found` (truncado), `Formato no soportado: ... (se aceptan
.apk, .aab, .xapk y .apks)` (ZIP sin contenido Android) y `El archivo no
existe: ...` (inexistente).

## Corridas de tamaño (criterio 5)

Las corridas usan este formato:

```bash
java -jar build/libs/aquedecomp.jar decompile <apk> -o <salida>
```

Ejecutadas el 2026-11-05 con el JAR de `gradle jar` en una máquina de
escritorio con Java 21. Todos los casos terminaron con `success: true` y
`toolVersion: "1.0.0"`.

| Tamaño | APK | Tiempo | Resultado |
|--------|-----|--------|-----------|
| Pequeña | smsscheduler.apk (50 KB) | 91 s | 16 archivos `.java`, efectividad 100.0, confianza media 79.4, paquete `com.github.yeriomin.smsscheduler` |
| Mediana | mini.apk (4.6 MB) | 519 s | 6425 archivos, efectividad 91.8, confianza media 68.0, motores: JADX 6360, CFR 63, Procyon 2 |
| Grande | signal.apk (109.7 MB) | 1699 s | 22006 archivos, efectividad 93.9, confianza media 65.7, motores: JADX 22003, CFR 3 |

Los tres APKs son apps reales de tamaños muy distintos. Las corridas incluyen
las siete etapas, incluida la puntuación de confianza de cada archivo.

## Formatos (criterio 1)

```bash
java -jar build/libs/aquedecomp.jar decompile testdata/fixtures/mini.xapk  -o salida-xapk
java -jar build/libs/aquedecomp.jar decompile testdata/fixtures/mini.apks  -o salida-apks
java -jar build/libs/aquedecomp.jar decompile testdata/fixtures/mini.aab   -o salida-aab
java -jar build/libs/aquedecomp.jar decompile testdata/mini.apk            -o salida-apk
```

Los fixtures se regeneran con `scripts/make-fixtures.ps1`; `mini.xapk`,
`mini.apks` y `mini.aab` contienen el mismo APK de origen (`mini.apk`), de modo
que las diferencias entre filas se deben solo al formato.

| Formato | `inputType` | Archivos `.java` | Manifiesto | Efectividad | Tiempo |
|---------|-------------|------------------|------------|-------------|--------|
| APK | APK | 6425 | binario, `com.soundcloud.android` | 91.8 | 519 s |
| XAPK | XAPK | 6425 | binario del APK interno, `com.soundcloud.android` | 91.8 | 599 s |
| APKS | APKS | 6425 | binario del APK interno, `com.soundcloud.android` | 91.8 | 510 s |
| AAB | AAB | 6424 | XML decodificado, `com.example.mini` | 91.0 | 499 s |

APK, XAPK y APKS producen resultados idénticos (mismo paquete, misma
efectividad, mismos 6425 archivos con la misma distribución de motores). El
AAB difiere en un archivo y en la ruta del manifiesto porque su estructura es
otra (`base/manifest/`, `base/dex/`).

Notas de implementación que sostienen estos resultados:

- **XAPK/APKS**: la normalización extrae el APK interno (`base.apk` o
  `base-master.apk`) a un archivo temporal; la inspección, el manifiesto y el
  rescate operan sobre ese APK y el temporal se borra al terminar.
- **AAB**: los DEX viven en `base/dex/` y dex2jar los acepta; el manifiesto es
  proto, así que si el binario no existe se lee el XML decodificado por JADX en
  `resources/AndroidManifest.xml`.
- **Fallback**: si JADX deja clases sin archivo, la cadena convierte a JAR con
  dex2jar y prueba CFR, Procyon y Vineflower en ese orden, copiando solo las
  clases que faltan.

## Motor por archivo (criterio 3)

En el JSON del informe:

```json
"engineByFile": {
  "com/example/Foo.java": "JADX",
  "com/example/Bar.java": "CFR"
},
"confidence": {
  "decompilerStats": { "JADX": 6360, "CFR": 63, "Procyon": 2 }
}
```

Toda clave de `engineByFile` es la ruta del archivo dentro de `sources/`; los
archivos que no aparecen en el mapa de rescate son del motor principal. En
Markdown y en consola aparece el resumen: `Motor que generó cada archivo: ...`.
