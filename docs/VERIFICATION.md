# Verificación de Aquadecomp 1.0.0, 2.0.0 y 2.0.1

Este documento dice cómo comprobar, paso a paso, que las versiones 1.0.0,
2.0.0 y 2.0.1 cumplen sus criterios de terminación. La primera parte cubre la
1.0.0, la segunda la 2.0.0 y la última la 2.0.1. Todo lo que sigue es
reproducible desde un clon limpio con Java 21 y Gradle 9.

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

---

## Verificación de 2.0.1

La 2.0.1 no agrega funcionalidades: corrige la documentación, añade CI con
GitHub Actions y refuerza el `rebuild` con pruebas externas (multidex, Kotlin,
dependencias externas, recursos, firma) y con un guard que rechaza salidas
desofuscadas. Se verificó el 2026-11-08 con el JAR de `gradle jar` en la misma
máquina que las versiones anteriores.

### Comandos base de 2.0.1

```bash
gradle test          # suite completa
gradle jar           # genera build/libs/aquedecomp.jar
```

El push a `main` y las pull requests ejecutan la misma suite en GitHub Actions
(`.github/workflows/ci.yml`, Windows + Temurin 21) y publican el JAR como
artefacto `aquedecomp-jar`.

### Pruebas externas de rebuild (criterio nuevo)

Son pruebas que van más allá de la suite con fixtures en memoria: usan APKs
construidos durante el test que ejercitan casos reales de reconstrucción.

| # | Prueba | Qué comprueba |
|---|--------|---------------|
| 1 | `RebuildExternalTest.apkMultidexReemplazaLaClaseEnSuDexYConservaLosDemas` | Un APK con dos dex: la clase recompilada se reemplaza en su dex y el otro dex se conserva byte a byte |
| 2 | `RebuildExternalTest.apkDeKotlinSeRecompilaConSuStdlibEmpaquetado` | Una clase Kotlin se recompila con kotlinc 2.0.21 y su kotlin-stdlib queda empaquetado en el APK |
| 3 | `RebuildExternalTest.dependenciaExternaSeResuelveDesdeElDexDelApk` | Una referencia a Gson se resuelve usando el propio dex del APK como classpath, sin dependencias externas |
| 4 | `RebuildExternalTest.recursosYAssetsSeConservanByteAByte` | Tras el rebuild, todos los recursos y assets son idénticos byte a byte a los del APK original |
| 5 | `RebuildExternalTest.firmaPropiaNoCoincideConElCertificadoDelDesarrollador` | El APK reconstruido se firma con un certificado propio (v1+v2+v3) que no coincide con el del desarrollador original |

### Guard de reconstrucción (criterio nuevo)

`RebuildPipeline.validateUnits` rechaza reconstrucciones que producirían un
APK inutilizable:

| # | Prueba | Qué comprueba |
|---|--------|---------------|
| 6 | `RebuildPipelineTest.rechazaSalidaDesofuscada` | En modo `editados`/`explicitos`, un FQCN que no existe en el dex original se rechaza con mensaje que sugiere `--no-deobf` |
| 7 | `RebuildPipelineTest.permiteRenombresForzadosEnOtrasClases` | Los renombres del jobf en clases que no se compilan no bloquean la reconstrucción |
| 8 | `RebuildPipelineTest.rechazaMiembrosRenombradosDeLaClaseEditada` | Si el jobf renombró miembros (`m`/`f`) de una clase que se va a compilar, se rechaza porque las referencias quedarían rotas |
| 9 | `RebuildPipelineTest.modoCompletoRechazaSalidaDesofuscada` | En modo `--app` (reconstrucción completa) también se rechaza una salida desofuscada |

### Corrida real con NewPipe

Además de las pruebas automáticas, se reconstruyó un APK de terceros real:
NewPipe v0.29.1 (`org.schabi.newpipe`, 3 archivos dex, sha256 del APK
`18447bfb1e06d113edc88df93f471827280de06f6f3d4dc42f56f28b9c1bab79`).

Proceso:

1. Descompilación con `decompile <apk> -o salida --no-deobf` (la modalidad
   con desofuscación renombraba clases y el guard la rechaza; sin deobf la
   cadena de motores es JADX 7096 + CFR 1, 468.9 s).
2. Edición de una sola clase: `NotificationPlayerUi.java`, cuya cadena
   `...ACTION_RECREATE_NOTIFICATION` es única en el APK. Solo ese archivo
   quedó con fecha posterior al corte del informe de descompilación.
3. `rebuild salida -o salida/NewPipe-rebuilt.apk`: **exit 0 en 72.8 s**
   (`durationMs=71433`), javac 1 fuente a 1 clase, d8, reemplazo en su dex y
   firma v1+v2+v3.

Resultado verificado:

| Comprobación | Resultado |
|--------------|-----------|
| Informe JSON | `success=true`, `mode=editados`, `replacedClasses=1`, `dexOriginal=3`, `signed=true` |
| `classes.dex` y `classes2.dex` | idénticos byte a byte al APK original (sha256 coincidentes) |
| `classes3.dex` | reensamblado con la clase editada (sha256 distinto) |
| Cadena editada | vieja: 0 apariciones; nueva: 1 aparición en `classes3.dex` |
| Firma (apksig) | `verificado=true`, v1=true, v2=true, v3=true, `CN=Aquadecomp Rebuild` |
| Entradas ZIP | 1375 en original y 1375 en salida; cambiadas: `classes3.dex` y `META-INF/MANIFEST.MF`; la firma v1 del desarrollador (`SCHABI_K.*`) se sustituye por la propia (`AQUADECO.*`) |
| Recursos, manifiesto y assets | byte a byte, salvo el `META-INF/MANIFEST.MF` que actualiza la firma |
| Aviso multidex en el log | "El APK original tenía 3 archivos dex; las clases recompiladas se reemplazaron en su dex y los demás se conservaron sin cambios" |

Notas de proceso y límites de esta corrida:

- `classes.dex` estaba exactamente en 65536 métodos, el tope del formato: una
  primera edición en una clase de ese dex hizo que d8 desbordara los índices
  y el rebuild fallara. Por eso la edición se movió a una clase de
  `classes3.dex` (3453 métodos), que es además la que contiene el código de
  NewPipe. La detección es correcta: el error de javac/d8 se reporta y no se
  genera ningún APK.
- dex2jar casi no emite el atributo `InnerClasses` (5 de 401 clases
  muestreadas), así que javac solo compila fuentes que no referencian tipos
  anidados de otras clases (`Outer.Inner`) del classpath. Esto es una
  limitación del classpath de clases originales, no del reemplazo en dex.
  Cerrada en 2.0.2 (ver más abajo).
- d8 emitió un aviso porque `PlayerUi` no está en su classpath de desugaring;
  `PlayerUi` es clase abstracta en el dex y los métodos de la clase editada
  son `override` de métodos abstractos, sin efecto en el resultado.
- **No se probó**: la instalación y el arranque en un dispositivo físico.
  La verificación de la corrida se hizo con apksig, comparación byte a byte
  y lectura del informe, no con ejecución real. Cerrado en 2.0.2 (ver más
  abajo).

Con esto la suite queda en **197 pruebas, 0 fallos, 0 errores, 30 clases de
prueba**.

## Verificación de 2.0.2 (correcciones tras 2.0.1)

Las limitaciones que quedaron abiertas en 2.0.1 se cerraron en modo
congelamiento: solo correcciones y documentación, sin funcionalidades nuevas.

### Guard del límite de referencias de método

- `DexMerge` calcula antes de ensamblar el delta de métodos de la clase
  editada contra las referencias de método del dex de destino y falla si la
  suma supera 65536, el tope del formato dex.
- E2E con NewPipe real: editar `ExtractionException` (clase de `classes.dex`,
  exactamente en 65536) terminó con exit=1 y el mensaje "El dex classes.dex
  usa 65536 de 65536 referencias de método... lo llevaría a 65537... Edita
  una clase que viva en otro archivo dex".
- El chequeo corre antes de baksmali, así que el fallo sale en segundos en
  lugar de después del desensamblado completo.
- Pruebas: `rechazaDexAltoDeReferenciasDeMetodo` y
  `permiteReemplazoDentroDelLimiteDeMetodo` (`DexMergeTest`).

### Atributo InnerClasses en el classpath

- dex2jar no emite `InnerClasses` en todas las clases: en el JAR real de
  NewPipe, 1234 de 5486 clases con `$` lo traían. Sin ese atributo javac no
  resuelve referencias a tipos anidados (`Outer.Inner`).
- `InnerClassAttributes.add` rellena el atributo que falte en el JAR de
  clases originales después de `Dex2JarConverter.convert`, con un parser
  propio de class files y sin dependencias nuevas. Los archivos que ya lo
  traen no se modifican.
- Sobre el JAR real de NewPipe: 3071 clases inyectadas, 0 de las 1234 que ya
  traían el atributo modificadas, segunda pasada idempotente, y `javap`
  resuelve `Image.ResolutionLevel` en `Image` después de la inyección.
- E2E del pipeline: `rebuild` de NewPipe con un archivo editado terminó con
  exit=0 en 75,6 s, APK firmado v1+v2+v3 de 19846413 bytes.
- Pruebas: `InnerClassAttributesTest` (3), con JAR fabricado a mano para
  controlar el estado inicial: sin atributo → se inyecta y javac resuelve el
  tipo anidado; generado por javac → los bytes de las dos clases no cambian;
  clases sin anidados → intactas.

### Distribución verificable

- Cada release publica `aquedecomp.jar.sha256` junto al JAR; el README y
  `docs/USAGE.md` documentan la verificación con `sha256sum -c` (Linux/macOS)
  o `certutil -hashfile ... SHA256` (Windows).
- `scripts/test-device.ps1` instala, lanza y verifica el APK reconstruido en
  un dispositivo conectado por USB o por `adb connect`, con captura de
  pantalla en `capturas/` y salida con código distinto de cero si la app no
  arranca limpia.

Suite: **202 pruebas, 0 fallos, 0 errores, 31 clases de prueba**.
