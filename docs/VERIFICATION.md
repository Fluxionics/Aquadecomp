# Verificación de Aquadecomp 1.0.0 y 2.0.0

Este documento dice cómo comprobar, paso a paso, que las versiones 1.0.0 y
2.0.0 cumplen sus criterios de terminación. La primera parte cubre la 1.0.0 y
la última sección, la 2.0.0. Todo lo que sigue es reproducible desde un clon
limpio con Java 21 y Gradle 9.

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

---

## Verificación de 2.0.0

La 2.0.0 agrega el flujo `rebuild` (edición de fuentes y APK firmado), mappings
de ProGuard/R8 con `--mapping`, la API REST (`serve`), el análisis de
bibliotecas nativas (`natives`), los plugins de encriptación de cadenas y la
inferencia de nombres (`nombres`). Se verificó el 2026-11-07 con el JAR final
(`gradle jar`, `toolVersion: "2.0.0"`) en la misma máquina que la verificación
de 1.0.0.

### Comandos base de 2.0.0

```bash
gradle test                                  # suite completa
gradle jar                                   # genera build/libs/aquedecomp.jar
java -Xmx6g -jar build/libs/aquedecomp.jar decompile <entrada> -o <salida>
java -jar build/libs/aquedecomp.jar rebuild <salida> --app -o <apk-nuevo>
java -jar build/libs/aquedecomp.jar natives <apk> --decompile
java -jar build/libs/aquedecomp.jar nombres <salida>/sources
java -jar build/libs/aquedecomp.jar serve --port 18101
```

Cada corrida usó un directorio de salida nuevo. Un directorio reutilizado de
una corrida interrumpida contamina la atribución de motores (ver
Reproducibilidad).

### Criterios de 2.0.0

| # | Criterio | Dónde se comprueba | Evidencia |
|---|----------|--------------------|-----------|
| 1 | `rebuild` recompila los fuentes y firma el APK | `RebuildPipelineTest`, `SourceCompilerTest`, `DexBuilderTest`, `DexMergeTest`, `ApkPackerTest` | E2E: `rebuild --app` compiló 2 archivos con javac + d8, reemplazó 2 clases en el dex, 0 errores, firma v1+v2+v3, informe `success: true` y `toolVersion: "2.0.0"` |
| 2 | `--mapping` restaura nombres de mappings ProGuard/R8 | `MappingFileTest`, `MappingApplierTest`, `DexMappingRewriterTest` | E2E con mapping de R8 8.7.18: los fuentes pasan de `p000a/C0000a.java` a `com/example/Foo.java` y `com/example/Bar.java`; log `Nombres restaurados en el dex: 2 clases, 4 miembros` |
| 3 | API REST con los mismos resultados que la CLI | `ApiServerTest` | E2E en el puerto 18101: `GET /` (versión 2.0.0, 7 endpoints), `GET /health`, `POST /api/inspect`, `POST /api/decompile` (`success: true`, efectividad 75.0), `POST /api/names` (1 sugerencia); cuerpo sin el campo requerido → 400; el servidor se detuvo al terminar |
| 4 | Análisis ELF real de los `.so`, con Ghidra solo si está instalado | `ElfParserTest`, `NativeAnalyzerTest`, `GhidraLocatorTest`, `GhidraRunnerTest` | E2E `natives signal.apk --decompile`: 48 bibliotecas con SONAME, dependencias, arquitectura y símbolos; sin Ghidra: mensaje `Ghidra no está disponible: no se encontró analyzeHeadless. Solo se realizó análisis ELF...` y código de salida 1 |
| 5 | Los plugins solo afirman descifrar con texto verificable | `DecryptionPluginsTest` (11), `StringDecryptionEngineTest` (12) | La sustitución ocurre solo si el texto descifrado es imprimible ASCII; los casos AES/RC4/XOR y la integración con el motor de recuperación están en la suite |
| 6 | La inferencia de nombres es reporte honesto, no aprendizaje automático | `NameInfererTest` (9), `ApiServerTest` | E2E: baseline de 2 archivos → 1 sugerencia (`f0a` → `valor`, confianza 30); app real de 16 archivos → 0 sugerencias; corpus de 58.416 archivos → 13.165 sugerencias en 134 s; siempre las 4 notas (reglas fijas, no aprendizaje automático, no modifica código, la confianza no garantiza el nombre original) |
| 7 | Los cuatro formatos y los tres tamaños siguen produciendo los mismos resultados | Tablas más abajo | APK, XAPK y APKS idénticos a 1.0.0; AAB idéntico salvo la variación de confianza anotada; pequeño y grande idénticos |
| 8 | Los APKs problemáticos no rompen el flujo | Corridas con entradas rotas | 5/5 con código de salida 1, `success: false` y mensajes en español |
| 9 | Suite completa en verde | `gradle test` | 176 pruebas, 0 fallos, 0 errores, 0 omitidas, 27 clases de prueba, 58 s |

### Corridas de formatos (criterio 7)

Ejecutadas el 2026-11-07 con `java -jar build/libs/aquedecomp.jar decompile
<entrada> -o <salida>`, todas con `success: true` y `toolVersion: "2.0.0"`:

| Formato | `inputType` | Archivos `.java` | Manifiesto | Efectividad | Confianza | Motores | Tiempo |
|---------|-------------|------------------|------------|-------------|-----------|---------|--------|
| APK | APK | 6425 | binario, `com.soundcloud.android` | 91.8 | 68.0 | JADX 6360, CFR 63, Procyon 2 | 563 s |
| XAPK | XAPK | 6425 | binario del APK interno, `com.soundcloud.android` | 91.8 | 68.0 | JADX 6360, CFR 63, Procyon 2 | 482 s |
| APKS | APKS | 6425 | binario del APK interno, `com.soundcloud.android` | 91.8 | 68.0 | JADX 6360, CFR 63, Procyon 2 | 492 s |
| AAB | AAB | 6424 | XML decodificado, `com.example.mini` | 91.0 | 67.9 | JADX 6359, CFR 63, Procyon 2 | 573 s |

APK, XAPK y APKS repiten exactamente los resultados de 1.0.0 (misma
confianza media entre las tres corridas: 67.95237354085603, que en consola
se muestra 68.0 como en 1.0.0). El AAB repite archivos, efectividad y
motores; su confianza media fue 67.9 (1.0.0: 68.0), una diferencia de menos
de 0.1 causada por la variación no determinista de JADX en unos pocos
archivos (ver Reproducibilidad).

### Corridas de tamaño (criterio 7)

| Tamaño | APK | Tiempo | Resultado |
|--------|-----|--------|-----------|
| Pequeña | smsscheduler.apk (50 KB) | 89 s | 16 archivos `.java`, efectividad 100.0, confianza media 79.4, paquete `com.github.yeriomin.smsscheduler`, motores: JADX 16 |
| Mediana | mini.apk (4.6 MB) | 563 s | 6425 archivos, efectividad 91.8, confianza media 68.0, motores: JADX 6360, CFR 63, Procyon 2 |
| Grande | signal.apk (109.7 MB) | 1636 s | 22006 archivos, efectividad 93.9, confianza media 65.7, motores: JADX 22003, CFR 3 |

Las tres corridas coinciden con las de 1.0.0 en archivos, efectividad,
confianza y distribución de motores.

### Entradas problemáticas (criterio 8)

Mismo procedimiento que en 1.0.0 y los mismos 5 casos (vacío, texto
renombrado a `.apk`, ZIP truncado, ZIP sin contenido Android y archivo
inexistente), ejecutados con el JAR de 2.0.0:

- código de salida `1`
- `aquedecomp-report.json` con `"success": false` y `errorMessage`
- mensajes en español: `No es un archivo ZIP válido: ...` (vacío y texto),
  `zip END header not found` (truncado), `Formato no soportado: ... (se aceptan
  .apk, .aab, .xapk y .apks)` (ZIP sin contenido Android) y `El archivo no
  existe: ...` (inexistente)

### Reproducibilidad frente a 1.0.0

- **APK, XAPK y APKS**: mismos 6425 archivos, misma efectividad (91.8), misma
  confianza media en las tres corridas y misma distribución de motores
  (JADX 6360, CFR 63, Procyon 2), igual que en 1.0.0.
- **AAB**: mismos 6424 archivos, misma efectividad (91.0), mismos 148 errores
  de descompilación, misma estructura válida (6145/6424) y misma distribución
  de motores que la corrida de 1.0.0 (JADX 6359, CFR 63, Procyon 2). Al
  comparar los árboles de fuentes byte a byte, 9 de 6424 archivos difieren
  entre corridas porque la salida de JADX no es determinista al 100 %; el resto
  es idéntico, incluidos los 65 archivos rescatados por CFR/Procyon. Esa
  variación mueve la confianza media de 68.0 a 67.9.
- **Tamaños**: pequeño y grande idénticos en todo (16/100.0/79.4 y
  22006/93.9/65.7 con JADX 22003 y CFR 3).
- **Nota de proceso**: la primera corrida del AAB se interrumpió a mitad
  después de dejar sus 65 archivos rescatados en la salida; la reejecución
  sobre ese directorio ya usado no volvió a rescatar y atribuyó esos archivos
  a JADX. Con un directorio limpio la corrida reprodujo el rescate 65/66 y la
  atribución exacta de 1.0.0. Por eso toda la matriz de 2.0.0 se corrió en
  salidas nuevas.
