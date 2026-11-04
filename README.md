# Aquadecomp - Multi-Herramienta Orquestador de Descompilación

Aquadecomp es una plataforma de descompilación de aplicaciones Android que integra múltiples descompiladores en una cadena de respaldo, maximizando la cobertura del código decompilado, incluso en apps ofuscadas.

## Características Principales

### v0.3: Motores de Recuperación (Experimental)

Aquadecomp v0.3 incluye motores de recuperación de código ofuscado (paquete `recovery`):

- **StringDecryptionEngine**: Recupera strings encriptados (XOR, byte arrays)
- **NameRecoveryEngine**: Recupera nombres reales desde múltiples fuentes (ProGuard, Kotlin, recursos)
- **DeobfuscationEngine**: Aplica mapeado de nombres y patrones de desofuscación
- **ClassRecoveryEngine**: Recupera clases internas, anónimas y faltantes
- **MetadataExtractor**: Extrae metadata de Kotlin, anotaciones y genéricos
- **RealCompilationVerifier**: Verifica con javac (compilación real, no heurística)

Estos motores son experimentales y todavía no forman parte del flujo principal de descompilación.

### Cadena de Descompiladores

En lugar de depender de una única herramienta, Aquadecomp usa una cadena multi-motor:

1. **JADX** - Motor principal, produce código Java legible directamente desde el DEX
2. **CFR** - Rescata las clases que JADX no pudo generar
3. **Procyon** - Segundo motor de rescate, especializado en Java moderno
4. **Vineflower** - Tercer motor de rescate, fork mantenido de Fernflower

Los motores de rescate reciben el APK convertido a JAR con dex2jar y solo procesan las clases que faltan en `sources/`, así se mejora la cobertura sin repetir trabajo.

**Resultado**: más clases recuperadas que con JADX solo, con el motor principal manteniendo la calidad del resto del código.

### Informe de Efectividad

El flujo principal calcula un puntaje de efectividad 0-100 con cuatro dimensiones:

- **Cobertura** (40%): clases con código sobre el total
- **Integridad** (25%): errores y advertencias de descompilación
- **Identificadores** (25%): pérdida de nombres por ofuscación
- **Recursos** (10%): recursos y manifiesto decodificados

El detalle sale en `aquedecomp-report.json` (`effectiveness`) y en el reporte Markdown.

El paquete `verification` incluye además un punteo por clase con niveles (Excelente/Bueno/Aceptable/Pobre/No confiable); forma parte del orquestador experimental, ver más abajo.

### Soporte Multi-Formato

- **.APK** - Paquetes Android estándar
- **.AAB** - Android App Bundles
- **.XAPK** - Paquetes expandidos
- **.APKS** - Splits APK

### Análisis Completo

- Inspección de manifiesto
- Detección de protectores/packers
- Análisis de ofuscación
- Estadísticas de descompilación (clases, errores, líneas)
- Informe de efectividad con metodología publicada

## Instalación

### Requisitos

- Java 21+
- 4GB RAM mínimo (6GB+ recomendado para APKs grandes)

### Compilar desde fuente

```bash
gradle jar
```

El JAR se genera en `build/libs/aquedecomp.jar`

También existe el wrapper (`gradlew.bat build` en Windows, `./gradlew build` en Linux/macOS).

### Ejecutable

```bash
java -jar build/libs/aquedecomp.jar [opciones] <APK_file> -o <output_dir>
```

## Uso

### CLI

```bash
java -jar aquedecomp.jar decompile signal.apk -o ./output

# Con opciones avanzadas
java -jar aquedecomp.jar decompile signal.apk \
  -o ./output \
  --threads 8 \
  --no-md \
  --no-json

# Solo inspeccionar el APK (sin descompilar)
java -jar aquedecomp.jar inspect signal.apk
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
│   ├── Dex2JarConverter.java            Conversión APK a JAR (dex2jar)
│   ├── JadxDecompilerWrapper.java       JADX wrapper
│   ├── VineflowerDecompiler.java        Rescate con Vineflower
│   ├── ProcyonDecompiler.java           Rescate con Procyon
│   └── CfrDecompiler.java               Rescate con CFR
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
├── sources/                 Código Java decompilado
├── resources/               Recursos (XML, assets, etc.)
├── AndroidManifest.xml     Manifiesto decodificado
├── aquedecomp-report.json  Reporte en formato JSON
├── aquedecomp-report.md    Reporte en formato Markdown
└── aquedecomp-renames.jobf Mapeo de renombrados (JADX)
```

## Reportes

### Reporte JSON

Contiene:
- Estructura del APK (DEX, libs nativas, packers detectados)
- Manifiesto (paquete, versión, permisos, actividad principal)
- Análisis de ofuscación (ratio, nivel, señales)
- Estadísticas de descompilación (clases, errores, líneas, duración)
- Informe de efectividad (puntaje global y por dimensión)

Ejemplo de estructura (campos reales):
```json
{
  "structure": { "apkPath": "...", "dexFiles": ["classes.dex"], "...": "..." },
  "manifest": { "packageName": "...", "permissions": ["..."], "...": "..." },
  "obfuscation": { "level": "MEDIA", "obfuscatedRatio": 0.67, "...": "..." },
  "stats": { "classesTotal": 46528, "errors": 12, "javaFiles": 43800, "...": "..." },
  "effectiveness": { "overallScore": 78.5, "coverageScore": 94.7, "...": "..." },
  "success": true,
  "toolVersion": "0.1.0-beta"
}
```

Ver `docs/USAGE.md` para el ejemplo completo.

### Reporte Markdown

Generado para lectura humana con:
- Efectividad global y desglose por dimensión
- Datos de la aplicación y permisos
- Señales de ofuscación detectadas
- Tabla de estadísticas
- Fortalezas, limitaciones y metodología del puntaje

## Configuración Avanzada

### Threads

```bash
java -jar aquedecomp.jar decompile signal.apk -o ./output --threads 16
```

### Sin Recursos

```bash
java -jar aquedecomp.jar decompile signal.apk -o ./output --no-resources
```

### Sin Desofuscación

```bash
java -jar aquedecomp.jar decompile signal.apk -o ./output --no-deobf
```

### Solo Resumen Final

```bash
java -jar aquedecomp.jar decompile signal.apk -o ./output --quiet
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

## Roadmap (Fases de Desarrollo)

### Fase 1: Base ( Completado v0.1)
- Cadena de descompiladores (JADX primario + rescate con CFR, Procyon y Vineflower)
- Sistema de confianza por clase
- Soporte multi-formato (APK/AAB/XAPK)
- Reportes JSON y Markdown

### Fase 2: Robustez ( Completado v0.2)
- Agregado Procyon como descompilador 5to
- CompilationVerifier para heurística de compilación
- SyntheticCodeDetector para código sintético
- Mejora de manejo de errores y tolerancia parcial

### Fase 3: Recuperación ( Completado v0.3)
- StringDecryptionEngine - Recuperación de strings
- NameRecoveryEngine - Recuperación de nombres desde múltiples fuentes
- DeobfuscationEngine - Desofuscación automática
- ClassRecoveryEngine - Recuperación de clases
- MetadataExtractor - Preservación de metadata
- RealCompilationVerifier - Verificación con javac real
- RecoveryPipeline - Orquestación de recuperación
- Integración con ConfidenceScorer (experimental)

### Fase 4: Optimización y Extensión (Próxima)
- [ ] Paralelización de motores de recuperación
- [ ] Caché de resultados de compilación
- [ ] Integración con Ghidra para código nativo (.so)
- [ ] Soporte para ProGuard/R8 mapping.txt
- [ ] API REST para integración
- [ ] ML-based name inference
- [ ] Plugins de encriptación personalizada

## Troubleshooting

### "Memoria agotada"

```bash
java -Xmx6g -jar aquedecomp.jar decompile large.apk -o ./output
```

### Clases sin descompilación

- Verificar reporte JSON: `statistics.smali_only`
- Revisar `aquedecomp-report.md` para detalles
- Revisar la fase "RESCATANDO CLASES" del log para ver qué motores actuaron

## Licencia

Apache License 2.0. Ver el archivo `LICENSE`.

## Contribuciones

Las contribuciones son bienvenidas. Por favor:

1. Fork el proyecto
2. Crea una rama feature
3. Envía Pull Request

## Soporte

Para reportar issues o sugerencias:
- GitHub Issues
- Contacto: support@aquadecomp.io
