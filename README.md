# Aquadecomp - Multi-Herramienta Orquestador de Descompilación

Aquadecomp es una plataforma avanzada de descompilación de aplicaciones Android que integra múltiples descompiladores en una cadena de respaldo, maximizando la cobertura y confiabilidad del código decompilado.

## Características Principales

### 🔄 Cadena de Descompiladores (Fallback Strategy)

En lugar de depender de una única herramienta, Aquadecomp intenta descompiladores en cascada:

1. **JADX** - Rápido y produce código Java legible
2. **Vineflower** - Fork mantenido de Fernflower, rescata clases donde JADX falla
3. **CFR** - Especializado en características modernas de Java
4. **Baksmali** - Último recurso, genera Smali puro (nunca falla)

**Resultado**: Cobertura 100%, aunque el formato varíe según disponibilidad.

### 📊 Sistema de Confianza por Clase

Cada clase decompilada recibe una puntuación de confianza basada en:

- Descompilador utilizado
- Presencia de advertencias
- Métodos sintetizados o faltantes
- Capacidad de compilación
- Indicators de encriptación

**Niveles de confianza**:
- 🟢 Excelente (90-100%)
- 🟢 Bueno (75-89%)
- 🟡 Aceptable (60-74%)
- 🟠 Pobre (40-59%)
- 🔴 No confiable (25-39%)
- ⚫ Solo Smali (<25%)

### 🎯 Soporte Multi-Formato

- **.APK** - Paquetes Android estándar
- **.AAB** - Android App Bundles
- **.XAPK** - Paquetes expandidos
- **.APKS** - Splits APK

### 📋 Análisis Completo

- Inspección de manifiesto
- Detección de protectores/packers
- Análisis de ofuscación
- Separación app vs librerías
- Recuperación de nombres
- Verificación por recompilación

## Instalación

### Requisitos

- Java 21+
- 4GB RAM mínimo (6GB+ recomendado para APKs grandes)

### Compilar desde fuente

```bash
./gradlew build
```

El JAR se genera en `build/libs/aquedecomp.jar`

### Ejecutable

```bash
java -jar aquedecomp.jar [opciones] <APK_file> -o <output_dir>
```

## Uso

### CLI

```bash
java -jar aquedecomp.jar decompile signal.apk -o ./output

# Con opciones avanzadas
java -jar aquedecomp.jar decompile signal.apk \
  -o ./output \
  --threads 8 \
  --include-resources \
  --deobfuscate \
  --show-bad-code
```

### GUI

```bash
java -jar aquedecomp.jar --gui
```

## Estructura del Proyecto

```
src/main/java/com/aquadecomp/
├── core/
│   ├── AquadecompOrchestrator.java      Orquestador principal
│   ├── DecompiledProject.java           Modelo de resultado
│   └── PipelineListener.java            Callbacks de progreso
├── decompilation/
│   ├── DecompilerStrategy.java          Interfaz de estrategia
│   ├── DecompilerChain.java             Cadena de respaldo
│   ├── JadxDecompilerWrapper.java       JADX wrapper
│   ├── VineflowerDecompiler.java        Vineflower wrapper
│   ├── CfrDecompiler.java               CFR wrapper
│   └── BaksmaliDecompiler.java          Baksmali wrapper
├── input/
│   └── InputNormalizer.java             Normaliza APK/AAB/XAPK
├── verification/
│   ├── ConfidenceScorer.java            Califica clases
│   ├── ConfidenceMap.java               Mapa de confianza
│   ├── ClassConfidence.java             Datos de confianza
│   └── ConfidenceLevel.java             Enum de niveles
└── reporting/
    └── ComprehensiveReportGenerator.java Genera reportes
```

## Archivos de Salida

```
output/
├── sources/                 Código Java/Smali decompilado
├── resources/               Recursos (XML, assets, etc.)
├── AndroidManifest.xml     Manifiesto decodificado
├── aquedecomp-report.json  Reporte en formato JSON
├── aquedecomp-report.md    Reporte en formato Markdown
└── aquedecomp-renames.jobf Mapeo de renombrados (JADX)
```

## Reportes

### Reporte JSON

Contiene:
- Metadatos del proyecto
- Información de descompilación
- Estadísticas completas de confianza
- Desglose por descompilador
- Desglose por formato

Ejemplo estructura:
```json
{
  "metadata": { ... },
  "decompilation": { ... },
  "statistics": {
    "total_classes": 5432,
    "average_score": "78.45",
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
    }
  }
}
```

### Reporte Markdown

Generado para lectura humana con:
- Resumen visual
- Tablas de estadísticas
- Gráficos de distribución
- Recomendaciones de calidad

## Configuración Avanzada

### Threads

```bash
java -jar aquedecomp.jar decompile signal.apk -o ./output --threads 16
```

### Incluir Recursos

```bash
java -jar aquedecomp.jar decompile signal.apk -o ./output --include-resources
```

### Mostrar Código Malo

```bash
java -jar aquedecomp.jar decompile signal.apk -o ./output --show-bad-code
```

### Desofuscación

```bash
java -jar aquedecomp.jar decompile signal.apk -o ./output --deobfuscate
```

## Performance

### Estimaciones de Tiempo

| Tamaño APK | Tiempo (1 thread) | Tiempo (8 threads) |
|------------|-------------------|-------------------|
| 5 MB      | 10-15s            | 5-8s               |
| 50 MB     | 1-2m              | 20-40s             |
| 150+ MB   | 5-10m             | 1-2m               |

### Consumo de Memoria

- Base: ~500 MB
- +200-400 MB por cada 10MB de APK
- Pico durante decompilación: 2-3x del tamaño del APK

### Optimizaciones

Para APKs grandes (>150MB):

```bash
java -Xmx8g -jar aquedecomp.jar decompile huge.apk -o ./output --threads 8
```

## Roadmap (Fase 2 y 3)

### Fase 2
- ✓ Cadena de descompiladores (JADX → Vineflower → CFR → Baksmali)
- ✓ Sistema de confianza por clase
- ✓ Soporte multi-formato (APK/AAB/XAPK)
- ✓ Reportes JSON y Markdown

### Fase 3 (Próxima)
- Ghidra headless para código nativo (.so)
- frida-dexdump para DEX cargado en runtime
- Desofuscación automática mejorada
- Mapping.txt de ProGuard/R8 integration
- API REST para integración

## Troubleshooting

### "Memoria agotada"

```bash
java -Xmx6g -jar aquedecomp.jar decompile large.apk -o ./output
```

### "No se encuentra baksmali"

Baksmali es opcional. Si no está instalado, la cadena continúa con Smali.

Instalar:
```bash
# Descargar de https://github.com/JesusFreke/smali/releases
# O usar gestor de paquetes del SO
```

### Clases sin descompilación

- Verificar reporte JSON: `statistics.smali_only`
- Revisar `aquedecomp-report.md` para detalles
- Aumentar `--show-bad-code` para ver intentos fallidos

## Licencia

Ver LICENSE

## Contribuciones

Las contribuciones son bienvenidas. Por favor:

1. Fork el proyecto
2. Crea una rama feature
3. Envía Pull Request

## Soporte

Para reportar issues o sugerencias:
- GitHub Issues
- Contacto: support@aquadecomp.io
