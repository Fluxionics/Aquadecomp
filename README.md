# Aquadecomp - Multi-Herramienta Orquestador de Descompilación

Aquadecomp es una plataforma de descompilación de aplicaciones Android que integra múltiples descompiladores en una cadena de respaldo, maximizando la cobertura del código decompilado, incluso en apps ofuscadas.

## Características Principales

### Flujo de Trabajo

`decompile` ejecuta un único flujo con todas las etapas conectadas:

1. **NORMALIZANDO ENTRADA** - valida el formato (APK/AAB/XAPK/APKS) y rechaza archivos no soportados
2. **INSPECCIONANDO APK** - estructura ZIP, DEX, libs nativas, packers
3. **DECODIFICANDO MANIFIESTO** - paquete, versión, permisos, actividad principal
4. **DESCOMPILANDO A JAVA** - JADX como motor principal, con rescate de clases faltantes mediante CFR, Procyon y Vineflower
5. **RECUPERANDO CÓDIGO** - descifrado de cadenas constantes, análisis de clases y metadatos, y verificación de sintaxis con javac sobre una muestra
6. **PUNTUANDO CONFIANZA** - puntaje 0-100 por clase con niveles (Excelente/Bueno/Aceptable/Pobre/No confiable)
7. **CALCULANDO EFECTIVIDAD** - puntaje global con cuatro dimensiones y reportes JSON + Markdown

### Motores de Recuperación (paquete `recovery`)

- **StringDecryptionEngine**: convierte literales constantes a cadenas legibles (`new String(new byte[]{...})`, `new String(Base64.decode("..."))`) sin alterar el resto del código
- **ClassRecoveryEngine**: detecta clases internas, anónimas y vacías en el código fuente
- **MetadataExtractor**: detecta metadatos de Kotlin, anotaciones y archivos de origen
- **RealCompilationVerifier**: verifica una muestra de archivos con `javac` real, distinguiendo errores de sintaxis de los de resolución de dependencias
- **RecoveryPipeline**: orquesta los motores sobre todos los archivos de `sources/`; los archivos modificados conservan una copia `.bak`

### Cadena de Descompiladores

En lugar de depender de una única herramienta, Aquadecomp usa una cadena multi-motor:

1. **JADX** - Motor principal, produce código Java legible directamente desde el DEX
2. **CFR** - Rescata las clases que JADX no pudo generar
3. **Procyon** - Segundo motor de rescate, especializado en Java moderno
4. **Vineflower** - Tercer motor de rescate, fork mantenido de Fernflower

Los motores de rescate reciben el APK convertido a JAR con dex2jar y solo procesan las clases que faltan en `sources/`, así se mejora la cobertura sin repetir trabajo.

**Resultado**: más clases recuperadas que con JADX solo, con el motor principal manteniendo la calidad del resto del código.

El informe indica el motor que generó cada archivo: `confidence.classes[].decompiler` guarda la etiqueta por archivo y `confidence.decompilerStats` resume cuántos archivos produjo cada motor. La etiqueta es metadato, no entra en el cálculo del puntaje.

### Informe de Efectividad

El flujo principal calcula un puntaje de efectividad 0-100 con cuatro dimensiones:

- **Cobertura** (40%): clases con código sobre el total
- **Integridad** (25%): errores y advertencias de descompilación
- **Identificadores** (25%): pérdida de nombres por ofuscación
- **Recursos** (10%): recursos y manifiesto decodificados

El detalle sale en `aquedecomp-report.json` (`effectiveness`) y en el reporte Markdown.

El paquete `verification` calcula además un puntaje por clase con niveles (Excelente/Bueno/Aceptable/Pobre/No confiable), que se incluye en el reporte bajo `confidence`.

### Soporte Multi-Formato

- **.APK** - Paquetes Android estándar
- **.AAB** - Android App Bundles: se leen los DEX en `base/dex/` y, si el manifiesto binario no existe, se usa el XML decodificado por JADX
- **.XAPK** - Paquetes expandidos: se extrae el APK interno (`base.apk`) para inspección, manifiesto y rescate
- **.APKS** - Splits APK: ídem con `base-master.apk`

Los cuatro formatos se validan en la etapa de normalización; un archivo que no es ZIP válido, que no contiene contenido Android o que tiene una extensión desconocida se rechaza con un mensaje claro y un informe de error generado igualmente.

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

### Pruebas

```bash
gradle test
```

La suite (JUnit 5) cubre la normalización de los cuatro formatos, entradas corruptas, la cadena de rescate con los tres motores, la proveniencia por motor, el manifiesto decodificado, el cálculo de efectividad y la escritura de reportes. Los fixtures de los formatos se regeneran con `scripts/make-fixtures.ps1` a partir de un APK pequeño.

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
├── Aquadecomp.java               Punto de entrada (CLI o GUI)
├── cli/
│   └── AquaCli.java              Comandos de línea (decompile, inspect)
├── gui/
│   └── AquaGui.java              Interfaz gráfica Swing
├── core/
│   ├── Pipeline.java             Flujo principal (etapas 1-7)
│   ├── PipelineListener.java     Callbacks de progreso
│   ├── PipelineResult.java       Resultado + reportes de recuperación
│   ├── ReportWriter.java         Reportes JSON y Markdown
│   ├── DecompileEngine.java      Integración con JADX
│   ├── ApkInspector.java         Estructura y manifiesto
│   ├── AxmlParser.java           XML binario del manifiesto
│   ├── EffectivenessCalculator.java  Puntaje de efectividad
│   └── ObfuscationDetector.java  Análisis de ofuscación
├── decompilation/
│   ├── DecompilerStrategy.java   Interfaz de estrategia
│   ├── DecompilerChain.java      Cadena de respaldo
│   ├── Dex2JarConverter.java    Conversión APK a JAR (dex2jar)
│   ├── JadxDecompilerWrapper.java  JADX wrapper
│   ├── VineflowerDecompiler.java   Rescate con Vineflower
│   ├── ProcyonDecompiler.java      Rescate con Procyon
│   └── CfrDecompiler.java          Rescate con CFR
├── input/
│   └── InputNormalizer.java      Validación y detección de formato
├── recovery/
│   ├── RecoveryPipeline.java     Orquestación de recuperación
│   ├── StringDecryptionEngine.java   Descifrado de literales constantes
│   ├── ClassRecoveryEngine.java      Análisis de estructura de clases
│   ├── MetadataExtractor.java        Metadatos Kotlin/anotaciones
│   └── RealCompilationVerifier.java  Verificación de sintaxis con javac
└── verification/
    ├── ConfidenceScorer.java     Puntaje por clase
    ├── ConfidenceMap.java        Mapa de confianza
    ├── ClassConfidence.java      Datos de confianza
    ├── ConfidenceLevel.java      Enum de niveles
    ├── CompilationVerifier.java  Verificación estructural heurística
    └── SyntheticCodeDetector.java  Detección de código generado/sintético
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
- Formato de entrada (`inputType`)
- Confianza por clase (`confidence`: conteos por nivel, puntaje medio)
- Recuperación (`recovery`: archivos procesados/modificados, muestra javac, clases Kotlin e internas)

Ejemplo de estructura (campos reales):
```json
{
  "structure": { "apkPath": "...", "dexFiles": ["classes.dex"], "...": "..." },
  "manifest": { "packageName": "...", "permissions": ["..."], "...": "..." },
  "obfuscation": { "level": "MEDIA", "obfuscatedRatio": 0.67, "...": "..." },
  "stats": { "classesTotal": 46528, "errors": 12, "javaFiles": 43800, "...": "..." },
  "effectiveness": { "overallScore": 78.5, "coverageScore": 94.7, "...": "..." },
  "inputType": "APK",
  "confidence": { "total": 6425, "averageScore": 68.0, "structurallyValid": 6146, "...": "..." },
  "recovery": { "filesProcessed": 6425, "filesModified": 0, "javacSampled": 40, "javacCompiled": 16, "...": "..." },
  "success": true,
  "toolVersion": "1.0.0"
}
```

Ver `docs/USAGE.md` para el ejemplo completo.

### Reporte Markdown

Generado para lectura humana con:
- Efectividad global y desglose por dimensión
- Datos de la aplicación y permisos
- Señales de ofuscación detectadas
- Tabla de estadísticas
- Recuperación de código (archivos, muestra javac, clases Kotlin e internas)
- Confianza por clase (tabla de niveles, puntaje medio, estructura válida)
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

### Tiempos medidos

Tiempos totales de las siete etapas (incluida la puntuación de confianza de
cada archivo) medidos en una máquina de escritorio con Java 21 y Gradle 9,
usando `java -jar aquedecomp.jar decompile <apk> -o <salida>`:

| APK                          | Tamaño  | Tiempo total |
|------------------------------|---------|--------------|
| smsscheduler.apk             | 50 KB   | 89 s         |
| mini.apk                     | 4.6 MB  | 465 s        |
| signal.apk                   | 109.7 MB| 1307 s       |

El tiempo lo dominan la descompilación con JADX y el análisis de confianza,
no la lectura del ZIP. Para reprocir estas medidas y las de los cuatro
formatos, ver [docs/VERIFICATION.md](docs/VERIFICATION.md).

### Consumo de Memoria

- Base: ~500 MB
- Observado en corridas de apps medianas (4.6 MB): ~750 MB de conjunto de trabajo
- APKs grandes: reservar más heap (ver abajo)

### Optimizaciones

Para APKs grandes (>100MB):

```bash
java -Xmx8g -jar aquedecomp.jar decompile huge.apk -o ./output --threads 8
```

## Roadmap (Fases de Desarrollo)

### Fase 1: Base (Completado v0.1)
- Cadena de descompiladores (JADX primario + rescate con CFR, Procyon y Vineflower)
- Sistema de confianza por clase
- Soporte multi-formato (APK/AAB/XAPK)
- Reportes JSON y Markdown

### Fase 2: Robustez (Completado v0.2)
- Agregado Procyon como descompilador 5to
- CompilationVerifier para heurística de compilación
- SyntheticCodeDetector para código sintético
- Mejora de manejo de errores y tolerancia parcial

### Fase 3: Recuperación (Completado v0.3, conectado al flujo)
- StringDecryptionEngine - descifrado de literales constantes
- ClassRecoveryEngine - análisis de estructura de clases
- MetadataExtractor - detección de metadatos
- RealCompilationVerifier - verificación de sintaxis con javac real
- RecoveryPipeline - orquestación de recuperación
- ConfidenceScorer - puntaje por clase integrado en Pipeline

### 1.0.0: Estabilización (Completado)
- Suite JUnit 5 con pruebas de normalización, entradas rotas, cadena de rescate, proveniencia por motor, manifiesto decodificado y reportes
- Los cuatro formatos (APK, AAB, XAPK, APKS) terminan con informe correcto
- Motor que generó cada archivo en el informe (`engineByFile`)
- Fixtures de formatos regenerables con `scripts/make-fixtures.ps1`
- Corridas de verificación pequeñas, medianas y grandes documentadas en `docs/VERIFICATION.md`

### Funciones congeladas

No se agregan motores, algoritmos ni características hasta una versión posterior.
Los criterios de 1.0.0 y cómo comprobarlos están en [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Troubleshooting

### "Memoria agotada"

```bash
java -Xmx6g -jar aquedecomp.jar decompile large.apk -o ./output
```

### Clases sin descompilación

- Revisar `aquedecomp-report.md` para detalles
- Revisar la fase "RESCATANDO CLASES" del log para ver qué motores actuaron
- Revisar el campo `stats.javaFiles` contra `stats.classesTotal` en el JSON

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
