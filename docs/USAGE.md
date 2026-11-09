# Guía de Uso - Aquadecomp

## Inicio Rápido

### Instalación Mínima

```bash
# Descargar JAR y checksum desde los releases del repositorio
curl -LO https://github.com/Fluxionics/Aquadecomp/releases/latest/download/aquedecomp.jar
curl -LO https://github.com/Fluxionics/Aquadecomp/releases/latest/download/aquedecomp.jar.sha256

# Verificar la integridad del archivo descargado
sha256sum -c aquedecomp.jar.sha256      # Linux / macOS
certutil -hashfile aquedecomp.jar SHA256  # Windows

# O compilar desde fuente
gradle jar
java -jar build/libs/aquedecomp.jar
```

Si el hash no coincide, el archivo está incompleto o alterado: descárgalo de
nuevo desde la página de la release.

### Primer Descompilación

```bash
# Comando básico
java -jar aquedecomp.jar decompile signal.apk -o ./output

# Sin recursos (más rápido)
java -jar aquedecomp.jar decompile signal.apk -o ./output --no-resources

# Con más memoria y threads
java -Xmx6g -jar aquedecomp.jar decompile signal.apk -o ./output --threads 8
```

## Línea de Comandos

### Sintaxis General

```bash
java [JVM_OPTIONS] -jar aquedecomp.jar COMMAND [OPTIONS] INPUT
```

### Comandos

#### `decompile`

Descompila un APK, AAB, XAPK o APKS.

```bash
java -jar aquedecomp.jar decompile <archivo> -o <salida> [opciones]
```

**Opciones**:

| Opción | Tipo | Default | Descripción |
|--------|------|---------|-------------|
| `-o, --out` | PATH | `./aquadecomp-out/<nombre>` | Directorio de salida |
| `--threads` | INT | 0 (auto) | Threads a usar |
| `--no-resources` | flag | apagado | No extraer recursos (solo código) |
| `--no-deobf` | flag | apagado | Desactivar el renombrado de nombres de JADX |
| `-q, --quiet` | flag | apagado | Solo mostrar el resumen final |
| `--no-md` | flag | apagado | No generar reporte Markdown |
| `--no-json` | flag | apagado | No generar reporte JSON |
| `--mapping` | PATH | sin mapping | Aplicar un mapping de ProGuard/R8 antes de descompilar |
| `--help` | - | - | Mostrar ayuda |

**Ejemplos**:

```bash
# Descompilación estándar
java -jar aquedecomp.jar decompile app.apk -o ./results

# Sin recursos (más rápido)
java -jar aquedecomp.jar decompile app.apk -o ./results --no-resources

# Máximo rendimiento
java -jar aquedecomp.jar decompile app.apk -o ./results --threads 16

# Sin desofuscación
java -jar aquedecomp.jar decompile app.apk -o ./results --no-deobf

# Combinado
java -jar aquedecomp.jar decompile app.apk -o ./results \
    --threads 8 \
    --no-md \
    --quiet
```

**Formatos de entrada**:

| Formato | Cómo lo procesa |
|---------|-----------------|
| `.apk` | Directo: estructura, manifiesto binario y DEX en la raíz |
| `.xapk` | Extrae `base.apk` a un archivo temporal para inspección, manifiesto y rescate |
| `.apks` | Ídem con `base-master.apk` |
| `.aab` | Lee los DEX en `base/dex/`; sin manifiesto binario usa el XML decodificado por JADX |

El campo `inputType` del informe indica el formato detectado; un archivo que no
es un paquete Android válido se rechaza en la etapa de normalización.

#### `inspect`

Solo inspecciona el APK (estructura, manifiesto, permisos, packers) sin descompilar.

```bash
java -jar aquedecomp.jar inspect app.apk
```

También acepta los cuatro formatos: en XAPK y APKS inspecciona el APK interno
extraído y lo indica en la salida (`Formato: XAPK (analizado sobre su APK
interno)`).

#### `rebuild`

Recompila las fuentes descompiladas y genera un APK firmado con los cambios.

```bash
java -jar aquedecomp.jar rebuild <directorio> -o <salida.apk> [opciones]
```

| Opción | Default | Descripción |
|--------|---------|-------------|
| `-o, --out` | `<directorio>/reconstruido.apk` | APK de salida |
| `--original` | detectado del reporte | APK original de donde salen recursos y manifiesto |
| `--android-jar` | `~/.aquadecomp/android.jar` | android.jar para compilar |
| `--mapping` | detectado (`aquedecomp-mapping.txt`) | Archivo de mapping |
| `--files` | todas | Fuentes a compilar (modo parche) |
| `--whole-app` | apagado | Recompilar la app completa |
| `--min-api` | 21 | API mínima del dex |

Los recursos, el manifiesto y los assets se conservan del APK original; el APK
se firma con un certificado propio de Aquadecomp, así que instalarlo exige
desinstalar antes la app original (se borran sus datos). Detalles y cómo
verificar la firma en [REBUILD.md](REBUILD.md).

```bash
# Editar fuentes y regenerar el APK
java -jar aquedecomp.jar rebuild ./results -o ./results/app-nuevo.apk
```

#### `natives`

Analiza las bibliotecas `.so` del paquete: ELF real (secciones, exports,
imports, dependencias) y Ghidra headless si está instalado.

```bash
java -jar aquedecomp.jar natives app.apk [--so TEXTO] [--decompile] [--ghidra DIR]
```

| Opción | Descripción |
|--------|-------------|
| `--so` | Filtra bibliotecas cuyo nombre contenga el texto |
| `--decompile` | Solicita desensamblado con Ghidra (si no está, se indica y solo se hace análisis ELF) |
| `--ghidra` | Directorio de instalación de Ghidra |
| `--out` | Directorio de salida de Ghidra |
| `--limit` | Máximo de exports impresos por biblioteca (default 10) |

Ver [NATIVES.md](NATIVES.md).

#### `nombres`

Sugiere nombres descriptivos para identificadores ofuscados en un directorio
de fuentes. Solo informe: no modifica ningún archivo.

```bash
java -jar aquedecomp.jar nombres <directorio-fuentes> [--out archivo.json] [--limit N]
```

Ver [NAME_INFERENCE.md](NAME_INFERENCE.md).

#### `serve`

Levanta la API REST local (JSON) en 127.0.0.1:8080 por defecto.

```bash
java -jar aquedecomp.jar serve [--host HOST] [--port N]
```

Ver [API.md](API.md) para endpoints y ejemplos.

#### `gui`

Inicia la interfaz gráfica. El comando y la opción `--gui` (equivalente)
aparecen en la ayuda de la CLI.

```bash
java -jar aquedecomp.jar gui

# Equivalente
java -jar aquedecomp.jar --gui
```

O simplemente sin argumentos:

```bash
java -jar aquedecomp.jar
```

La ventana tiene una pestaña por función: **Descompilar**, **Analizar**
(inspect), **Recompilar**, **Nativos** (bibliotecas `.so`) y **Nombres**
(sugerencias). El botón inferior ejecuta la función de la pestaña activa y
los resultados se muestran en las pestañas Reporte y Registro.

### Opciones de JVM

```bash
# Aumentar memoria
java -Xmx8g -jar aquedecomp.jar decompile app.apk -o ./out

# Incluir GC log
java -Xmx8g -Xlog:gc -jar aquedecomp.jar decompile app.apk -o ./out

# Threads de GC
java -Xmx8g -XX:ParallelGCThreads=8 -jar aquedecomp.jar decompile app.apk -o ./out
```

## Casos de Uso

### Caso 1: Análisis de Seguridad Rápido

```bash
# Objetivo: Revisar el código principal rápidamente
java -jar aquedecomp.jar decompile suspicious.apk -o ./analysis \
    --threads $(nproc) \
    --no-md

# Revisa:
# 1. aquedecomp-report.json para resumen
# 2. Clases con score EXCELLENT (90-100)
# 3. Salta las clases POOR/UNRELIABLE
```

### Caso 2: Descompilación Completa con Recursos

```bash
# Objetivo: Obtener código + recursos + manifiesto
# (los recursos se extraen por defecto)
java -jar aquedecomp.jar decompile app.apk -o ./full_decompile

# Salida:
# - sources/       → Código Java
# - resources/     → Imágenes, layouts, etc.
# - AndroidManifest.xml
# - aquedecomp-report.json/md
```

### Caso 3: APK Protegido o Empacado

```bash
# Objetivo: Forzar todos los motores
java -Xmx12g -jar aquedecomp.jar decompile protected.apk -o ./protected_analysis \
    --threads 16

# Lee el reporte:
# - Revisa confidence (conteos por nivel y puntaje medio)
# - Nota: Si averageScore < 50, probablemente empacado
```

### Caso 4: AAB (Android App Bundle)

```bash
# Objetivo: Descompilación de AAB con módulos
java -jar aquedecomp.jar decompile app.aab -o ./aab_analysis

# Nota: Se extraen todos los módulos automáticamente
```

### Caso 5: Análisis Comparativo

```bash
# Descompila con máximo detalle
java -jar aquedecomp.jar decompile app_v1.apk -o ./v1_analysis
java -jar aquedecomp.jar decompile app_v2.apk -o ./v2_analysis

# Compara:
# diff v1_analysis/sources v2_analysis/sources
# Compara reports: v1_analysis/aquedecomp-report.json vs v2_analysis/aquedecomp-report.json
```

## Interpretación de Resultados

### Directorio de Salida

```
output/
├── sources/                     # Código decompilado
│   ├── com/
│   │   ├── signal/
│   │   │   ├── MainActivity.java
│   │   │   └── utils/
│   │   └── androidx/            # Librerías incluidas
│   └── ...
├── resources/                   # Recursos extraídos (con --no-resources no se extraen)
│   ├── res/
│   │   ├── layout/
│   │   ├── values/
│   │   └── drawable/
│   ├── assets/
│   └── ...
├── AndroidManifest.xml          # Manifiesto decodificado
├── aquedecomp-renames.jobf      # Mapeo JADX (si usado)
├── aquedecomp-report.json       # Reporte estructurado
└── aquedecomp-report.md         # Reporte legible
```

### Reporte JSON

Estructura (serialización Gson de `PipelineResult`), con valores ilustrativos:

```json
{
  "structure": {
    "apkPath": "C:/apk-work/app.apk",
    "sizeBytes": 53382144,
    "entryCount": 12873,
    "dexFiles": ["classes.dex", "classes2.dex", "classes3.dex"],
    "nativeLibs": ["lib/arm64-v8a/libnative.so"],
    "assetEntries": ["assets/config.json"],
    "detectedPackers": [],
    "warnings": [],
    "hasBinaryManifest": true,
    "hasResourcesTable": true,
    "hasMultiDex": true,
    "hasHiddenDex": false
  },
  "manifest": {
    "packageName": "com.example.app",
    "versionName": "1.2.3",
    "versionCode": "123",
    "applicationClass": "com.example.app.App",
    "debuggable": false,
    "allowBackup": true,
    "permissions": ["android.permission.INTERNET"],
    "activities": ["com.example.app.MainActivity"],
    "mainActivity": "com.example.app.MainActivity",
    "parsedOk": true,
    "parseError": null
  },
  "obfuscation": {
    "totalClasses": 4600,
    "obfuscatedClasses": 3100,
    "totalMethods": 41000,
    "obfuscatedMethods": 35000,
    "totalFields": 18000,
    "obfuscatedFields": 15000,
    "obfuscatedPackages": 12,
    "substitutedIds": 0,
    "obfuscatedRatio": 0.67,
    "substitutedRatio": 0.0,
    "level": "MEDIA",
    "signals": ["Nombres de clases cortos (a, b, c)", "Paquetes ofuscados"],
    "likelyStringEncryption": false
  },
  "stats": {
    "classesTotal": 46528,
    "classesWithCode": 44100,
    "classesNoCode": 2428,
    "errors": 12,
    "warns": 430,
    "javaFiles": 43800,
    "sourceLines": 5120000,
    "resourcesDecoded": true,
    "manifestParsed": true,
    "durationMs": 45230
  },
  "effectiveness": {
    "overallScore": 78.5,
    "coverageScore": 94.7,
    "integrityScore": 88.0,
    "identifierScore": 62.0,
    "resourceScore": 95.0,
    "coverageDetail": "44100/46528 clases con código",
    "integrityDetail": "12 errores de descompilación",
    "identifierDetail": "ofuscación MEDIA",
    "resourceDetail": "recursos decodificados",
    "strengths": ["Alta cobertura de clases"],
    "limitations": ["Nombres ofuscados sin mapping.txt"],
    "methodology": "0.40*Cobertura + 0.25*Integridad + 0.25*Identificadores + 0.10*Recursos"
  },
  "inputType": "APK",
  "confidence": {
    "total": 6425,
    "excellent": 812,
    "good": 2410,
    "acceptable": 1988,
    "poor": 955,
    "unreliable": 260,
    "averageScore": 68.0,
    "structurallyValid": 6146,
    "decompilerStats": { "JADX": 6360, "CFR": 63, "Procyon": 2 }
  },
  "recovery": {
    "filesProcessed": 6425,
    "filesModified": 0,
    "javacSampled": 40,
    "javacCompiled": 16,
    "kotlinClasses": 4940,
    "innerClasses": 773,
    "items": {},
    "durationMs": 209043
  },
  "engineByFile": {
    "com/soundcloud/android/App.java": "JADX",
    "com/soundcloud/android/Falta.java": "CFR"
  },
  "outputDir": "./output",
  "success": true,
  "errorMessage": null,
  "toolVersion": "2.0.1"
}
```

### Reporte Markdown

Ejemplo de estructura (valores ilustrativos; lo real depende del APK):

```markdown
# Aquadecomp — Reporte de descompilación

- **Fecha:** 2026-11-04T13:05:00Z
- **APK:** C:/apk-work/app.apk
- **Tamaño:** 50.91 MB
- **Versión Aquadecomp:** 2.0.1
- **Entrada:** APK

## Efectividad global: 78.5/100

| Dimensión | Puntaje | Detalle |
|---|---:|---|
| Cobertura | 94.7 | 44100/46528 clases con código |
| Integridad | 88.0 | 12 errores de descompilación |
| Identificadores | 62.0 | ofuscación MEDIA |
| Recursos | 95.0 | recursos decodificados |

## Aplicación

- **Paquete:** com.example.app
- **Versión:** 1.2.3 (123)
- **Actividad principal:** com.example.app.MainActivity
- **Permisos:** 14
  - `android.permission.INTERNET`
- **Debuggable:** false

## Ofuscación detectada: MEDIA

- Nombres de clases cortos (a, b, c)
- Paquetes ofuscados

## Estadísticas

| Métrica | Valor |
|---|---:|
| Clases totales | 46528 |
| Clases con código | 44100 |
| Clases sin código | 2428 |
| Errores / advertencias | 12 / 430 |
| Archivos .java | 43800 |
| Líneas de código | 5120000 |
| Duración | 45.2 s |

## Recuperación de código

| Métrica | Valor |
|---|---:|
| Archivos procesados | 6425 |
| Archivos modificados | 0 |
| Muestra javac sin errores de sintaxis | 16 / 40 |
| Clases con metadatos Kotlin | 4940 |
| Clases internas detectadas | 773 |
| Duración | 209.0 s |

## Confianza por clase

| Nivel | Clases |
|---|---:|
| Excelente | 812 |
| Bueno | 2410 |
| Aceptable | 1988 |
| Pobre | 955 |
| No confiable | 260 |
| **Total** | 6425 |

- Puntaje medio: 68.0/100
- Fiables (excelente + bueno + aceptable): 81.0%
- Estructura válida (heurística, no compilación real): 6146/6425 (95.7%)
- Motor que generó cada archivo: JADX 6360, CFR 63, Procyon 2

## Fortalezas

- Alta cobertura de clases

## Limitaciones

- Nombres ofuscados sin mapping.txt

## Metodología del puntaje

0.40*Cobertura + 0.25*Integridad + 0.25*Identificadores + 0.10*Recursos
```

## Performance y Troubleshooting

### Tiempo de Ejecución

**Estimaciones**:

| APK Size | Single Thread | 8 Threads | 16 Threads |
|----------|---------------|-----------|------------|
| 5 MB     | 15s           | 5s        | 3s         |
| 50 MB    | 2m            | 30s       | 20s        |
| 150 MB   | 8m            | 2m        | 1m         |
| 300+ MB  | 20m+          | 5m+       | 3m+        |

### Optimizaciones

#### Para APKs Grandes

```bash
# Aumentar memoria y threads
java -Xmx12g -XX:+UseG1GC \
    -jar aquedecomp.jar decompile large.apk -o ./out \
    --threads 16
```

#### Para Máquinas Lentas

```bash
# Menos threads, más tiempo
java -Xmx2g -jar aquedecomp.jar decompile app.apk -o ./out --threads 2
```

#### Sin Recursos (más rápido)

```bash
# Solo código, sin recursos
java -jar aquedecomp.jar decompile app.apk -o ./out --no-resources
```

### Problemas Comunes

#### "OutOfMemoryError"

```bash
# Aumentar heap
java -Xmx8g -jar aquedecomp.jar decompile app.apk -o ./out
```

#### "Clases sin rescatar" en el log

```bash
# Algunas clases no las genera ningún motor
# Revisar el log de la fase "RESCATANDO CLASES"
# y el reporte JSON para ver cuántas faltan
```

#### Descompilación Muy Lenta

```bash
# Aumentar threads
java -jar aquedecomp.jar decompile app.apk -o ./out --threads 16

# O usar menos recursos pero más tiempo
java -Xmx4g -jar aquedecomp.jar decompile app.apk -o ./out --threads 4
```

#### Reportes Muestran Score Bajo

```bash
# Probable: APK protegido o empacado
# Soluciones:

# 1. Verificar warnings de JADX
grep "JADX WARNING" output/sources/**/*.java | wc -l

# 2. Revisar la fase de rescate en el log
grep "RESCATANDO" aquedecomp.log

# 3. Considerar análisis dinámico (futuro)
```

## Integración con Otras Herramientas

### Con Git

```bash
# Inicializar repo
cd output/sources
git init
git add .
git commit -m "Decompiled with Aquadecomp"

# Comparar versiones
git diff
```

### Con IDEs

#### Android Studio

```bash
# Copiar sources a nuevo proyecto
cp output/sources/* ~/projects/MyProject/app/src/main/java/

# Abrir en Android Studio
studio ~/projects/MyProject
```

#### IntelliJ IDEA

```bash
# Marcar como fuentes
File > Project Structure > Modules
# Agregar output/sources como Source Folder
```

### Con Otros Decompiladores

```bash
# Comparar con CFR directo
cfr app.apk --outputdir ./cfr_output

# Comparar reportes
diff <(jq .confidence.averageScore aquedecomp-report.json) \
     <(tu_otro_tool_report.json)
```

## Scripting

### Descompilación en Lotes

```bash
#!/bin/bash
# decompile_batch.sh

for apk in *.apk; do
    echo "Descompilando $apk..."
    java -jar aquedecomp.jar decompile "$apk" \
        -o "./output/${apk%.apk}" \
        --threads 8
done
```

### Extracción de Reportes

```bash
#!/bin/bash
# extract_stats.sh

for report in */aquedecomp-report.json; do
    app=$(dirname "$report")
    score=$(jq .confidence.averageScore "$report")
    valid=$(jq .confidence.structurallyValid "$report")
    echo "$app: confianza=$score, estructura válida=$valid"
done
```

### Pipeline Completo

```bash
#!/bin/bash
# full_analysis.sh

APK="$1"
OUTPUT="./analysis"

# 1. Descompila
java -Xmx8g -jar aquedecomp.jar decompile "$APK" -o "$OUTPUT"

# 2. Genera reportes
cd "$OUTPUT"

# 3. Cuenta clases
echo "Total clases: $(find sources -name '*.java' | wc -l)"

# 4. Busca problemas
echo "JADX Warnings: $(grep -r 'JADX WARNING' sources | wc -l)"
echo "Métodos no implementados: $(grep -r 'UnsupportedOperationException' sources | wc -l)"

# 5. Abre reporte
cat aquedecomp-report.md
```

## Próximos Pasos

1. **Lee el Reporte**: Entiende la calidad del código
2. **Filtra por Confianza**: Enfócate en EXCELLENT/GOOD
3. **Verifica Compilabilidad**: ¿Qué % compila?
4. **Mapea el Código**: Entiende estructura y flujo
5. **Documenta Hallazgos**: Mantén notas en markdown

## Soporte

- **Issues**: GitHub Issues
- **Discussiones**: GitHub Discussions
- **Email**: support@aquadecomp.io
