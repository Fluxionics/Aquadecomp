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
| 1 | Procesa APK, AAB, XAPK y APKS | `InputNormalizerTest`, `ApkInspectorTest`, corridas de los 4 formatos | Los cuatro formatos terminan con `success: true`, `inputType` correcto y el mismo número de archivos `.java` que el APK de origen |
| 2 | Cuatro motores reales con fallback comprobado | `DecompilerStrategyTest` (CFR, Procyon, Vineflower rescatan una clase compilada en el test) + corridas con rescate en `sources/` | `gradle test` prueba cada motor con una clase conocida; en corridas reales el log muestra `Motor CFR: N clases rescatadas` |
| 3 | Identifica el motor de cada archivo | `ConfidenceScorerTest`, `ReportWriterTest` | `aquedecomp-report.json` incluye `engineByFile` (ruta relativa → motor) y `confidence.decompilerStats` con el resumen por motor |
| 4 | Confianza sin puntuaciones engañosas | `ConfidenceScorerTest.engineIdentityDoesNotChangeTheScore`, `EffectivenessCalculatorTest`, `ConfidenceLevelTest` | El motor que produjo un archivo es metadato y no altera el puntaje; la metodología va escrita en cada informe |
| 5 | Pruebas con apps pequeñas, medianas y grandes | Tabla de corridas más abajo | Tres APKs de tamaños muy distintos, mismos comandos |
| 6 | Informes correctos y APKs problemáticos no lo rompen | `PipelineFailureTest`, `ReportWriterTest`, `InputNormalizerTest`, corridas con entradas rotas | Una entrada inválida termina con código de salida 1, mensaje en español y `aquedecomp-report.json` con `success: false` |
| 7 | Documentación clara y resultados reproducibles | Este archivo, `README.md`, `docs/` | Comandos exactos, fixtures regenerables y tabla de resultados |

## Entradas problemáticas

Caso de uso: cualquier archivo que no sea un paquete Android válido debe
rechazarse sin traza de pila y dejando informe de error.

```bash
java -jar build/libs/aquedecomp.jar decompile entrada-invalida.apk -o salida
echo $?    # 1
```

Comportamiento esperado para: archivo de 0 bytes, texto renombrado a `.apk`,
ZIP truncado, ZIP sin contenido Android y archivo inexistente:

- código de salida `1`
- mensaje `Falló: ...` en español, sin traza de pila
- `salida/aquedecomp-report.json` con `"success": false` y `errorMessage`

## Corridas de tamaño (criterio 5)

Las corridas usan este formato:

```bash
java -jar build/libs/aquedecomp.jar decompile <apk> -o <salida>
```

| Tamaño | APK | Entrada | Resultado |
|--------|-----|---------|-----------|
| Pequeña | smsscheduler.apk (50 KB) | pendiente de la corrida final | pendiente |
| Mediana | mini.apk (4.6 MB) | pendiente de la corrida final | pendiente |
| Grande | signal.apk (109.7 MB) | pendiente de la corrida final | pendiente |

## Formatos (criterio 1)

```bash
java -jar build/libs/aquedecomp.jar decompile testdata/fixtures/mini.xapk  -o salida-xapk
java -jar build/libs/aquedecomp.jar decompile testdata/fixtures/mini.apks  -o salida-apks
java -jar build/libs/aquedecomp.jar decompile testdata/fixtures/mini.aab   -o salida-aab
java -jar build/libs/aquedecomp.jar decompile testdata/mini.apk            -o salida-apk
```

| Formato | `inputType` | Archivos `.java` | Manifiesto | Resultado |
|---------|-------------|------------------|------------|-----------|
| APK | pendiente | pendiente | pendiente | pendiente |
| XAPK | pendiente | pendiente | pendiente | pendiente |
| APKS | pendiente | pendiente | pendiente | pendiente |
| AAB | pendiente | pendiente | pendiente | pendiente |

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
