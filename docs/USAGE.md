# Guía de Uso - Aquadecomp

## Inicio Rápido

### Instalación Mínima

```bash
# Descargar JAR
curl -O https://github.com/aquadecomp/releases/latest/aquedecomp.jar

# O compilar desde fuente
./gradlew build
java -jar build/libs/aquedecomp.jar
```

### Primer Descompilación

```bash
# Comando básico
java -jar aquedecomp.jar decompile signal.apk -o ./output

# Con recursos
java -jar aquedecomp.jar decompile signal.apk -o ./output --include-resources

# Con optimizaciones
java -jar aquedecomp.jar -Xmx6g decompile signal.apk -o ./output --threads 8
```

## Línea de Comandos

### Sintaxis General

```bash
java [JVM_OPTIONS] -jar aquedecomp.jar COMMAND [OPTIONS] INPUT
```

### Comandos

#### `decompile`

Descompila un APK, AAB o XAPK.

```bash
java -jar aquedecomp.jar decompile <archivo> -o <salida> [opciones]
```

**Opciones**:

| Opción | Tipo | Default | Descripción |
|--------|------|---------|-------------|
| `-o, --output` | PATH | required | Directorio de salida |
| `--threads` | INT | 0 (auto) | Threads a usar |
| `--include-resources` | BOOLEAN | false | Extraer recursos |
| `--deobfuscate` | BOOLEAN | true | Intentar desofuscación |
| `--show-bad-code` | BOOLEAN | true | Mostrar código problemático |
| `--help` | - | - | Mostrar ayuda |

**Ejemplos**:

```bash
# Descompilación estándar
java -jar aquedecomp.jar decompile app.apk -o ./results

# Con todos los recursos
java -jar aquedecomp.jar decompile app.apk -o ./results --include-resources

# Máximo rendimiento
java -jar aquedecomp.jar decompile app.apk -o ./results --threads 16

# Mostrar incluso código malo
java -jar aquedecomp.jar decompile app.apk -o ./results --show-bad-code

# Sin desofuscación
java -jar aquedecomp.jar decompile app.apk -o ./results --deobfuscate=false

# Combinado
java -jar aquedecomp.jar decompile app.apk -o ./results \
    --threads 8 \
    --include-resources \
    --deobfuscate \
    --show-bad-code
```

#### `gui`

Inicia la interfaz gráfica.

```bash
java -jar aquedecomp.jar gui
```

O simplemente sin argumentos:

```bash
java -jar aquedecomp.jar
```

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
    --show-bad-code

# Revisa:
# 1. aquedecomp-report.md para resumen
# 2. Clases con score EXCELLENT (90-100)
# 3. Salta las clases POOR/UNRELIABLE
```

### Caso 2: Descompilación Completa con Recursos

```bash
# Objetivo: Obtener código + recursos + manifiesto
java -jar aquedecomp.jar decompile app.apk -o ./full_decompile \
    --include-resources \
    --deobfuscate

# Salida:
# - sources/       → Código Java
# - resources/     → Imágenes, layouts, etc.
# - AndroidManifest.xml
# - aquedecomp-report.json/md
```

### Caso 3: APK Protegido o Empacado

```bash
# Objetivo: Forzar all the tools
java -Xmx12g -jar aquedecomp.jar decompile protected.apk -o ./protected_analysis \
    --threads 16 \
    --include-resources \
    --show-bad-code

# Lee el reporte:
# - Verifica stats.smali_only (si es alto, protegido)
# - Revisa confidence_breakdown
# - Nota: Si score_avg < 50%, probablemente empacado
```

### Caso 4: AAB (Android App Bundle)

```bash
# Objetivo: Descompilación de AAB con módulos
java -jar aquedecomp.jar decompile app.aab -o ./aab_analysis \
    --include-resources

# Nota: Se extraen todos los módulos automáticamente
```

### Caso 5: Análisis Comparativo

```bash
# Descompila con máximo detalle
java -jar aquedecomp.jar decompile app_v1.apk -o ./v1_analysis --include-resources
java -jar aquedecomp.jar decompile app_v2.apk -o ./v2_analysis --include-resources

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
├── resources/                   # Recursos extraídos (si --include-resources)
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

Estructura completa:

```json
{
  "metadata": {
    "project_name": "signal.apk",
    "created_at": "2024-11-02T14:32:00Z",
    "aquadecomp_version": "0.1.0-beta",
    "duration_ms": 45230
  },
  "decompilation": {
    "primary_decompiler": "JADX",
    "attempts_made": ["JADX"],
    "source_directory": "/output/sources",
    "resources_directory": "/output/resources"
  },
  "statistics": {
    "total_classes": 5432,
    "average_score": "78.45",
    "quality_label": "Muy bueno",
    "success_ratio": "82.33%",
    "confidence_breakdown": {
      "excellent": 2156,
      "good": 1890,
      "acceptable": 965,
      "poor": 312,
      "unreliable": 109,
      "smali_only": 0
    },
    "compilable": {
      "count": 4546,
      "percentage": "83.65%"
    },
    "decompiler_usage": {
      "JADX": 5432
    },
    "format_breakdown": {
      "JAVA": 5432,
      "SMALI": 0
    }
  }
}
```

### Reporte Markdown

Ejemplo:

```markdown
# Aquadecomp - Reporte de Descompilación

## Información del Proyecto
- Nombre: signal.apk
- Fecha: 2024-11-02 14:32:00
- Versión Aquadecomp: 0.1.0-beta
- Tiempo total: 45.23s

## Descompilación
- Descompilador principal: JADX
- Intentos realizados: 1

## Resultados

### Calidad General
- Etiqueta de calidad: Muy bueno
- Puntuación promedio: 78.45%
- Ratio de éxito: 82.33%

### Desglose de Confianza
| Nivel | Cantidad | Porcentaje |
|-------|----------|-----------|
| Excelente | 2156 | 39.71% |
| Bueno | 1890 | 34.80% |
| Aceptable | 965 | 17.77% |
| Pobre | 312 | 5.74% |
| No confiable | 109 | 2.01% |
| Solo Smali | 0 | 0.00% |

### Compilabilidad
- Clases compilables: 4546 (83.65%)

### Distribución por Descompilador
- JADX: 5432

### Formatos de Salida
- JAVA: 5432
- SMALI: 0
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
# Solo código
java -jar aquedecomp.jar decompile app.apk -o ./out
# (default no incluye recursos)
```

### Problemas Comunes

#### "OutOfMemoryError"

```bash
# Aumentar heap
java -Xmx8g -jar aquedecomp.jar decompile app.apk -o ./out
```

#### "baksmali: command not found"

```bash
# Baksmali es opcional - ignora el error
# O instálalo:

# En macOS
brew install smali

# En Linux
# Descargar de https://github.com/JesusFreke/smali/releases

# En Windows
# Descargar release y agregar a PATH
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

# 1. Verificar si hay código Smali (signo de protección)
grep -c "\.smali" output/sources

# 2. Verificar warnings
grep "JADX WARNING" output/sources/**/*.java | wc -l

# 3. Usar Baksmali manualmente para bytecode puro
baksmali d app.apk -o ./smali_output

# 4. Considerar análisis dinámico (futuro)
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
diff <(jq .statistics.average_score aquedecomp-report.json) \
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
        --threads 8 \
        --include-resources
done
```

### Extracción de Reportes

```bash
#!/bin/bash
# extract_stats.sh

for report in */aquedecomp-report.json; do
    app=$(dirname "$report")
    score=$(jq .statistics.average_score "$report")
    compilable=$(jq .statistics.compilable.percentage "$report")
    echo "$app: score=$score, compilable=$compilable"
done
```

### Pipeline Completo

```bash
#!/bin/bash
# full_analysis.sh

APK="$1"
OUTPUT="./analysis"

# 1. Descompila
java -Xmx8g -jar aquedecomp.jar decompile "$APK" -o "$OUTPUT" --include-resources

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
