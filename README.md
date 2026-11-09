# Aquadecomp - Multi-Herramienta Orquestador de Descompilación

[![CI](https://github.com/Fluxionics/Aquadecomp/actions/workflows/ci.yml/badge.svg)](https://github.com/Fluxionics/Aquadecomp/actions/workflows/ci.yml)

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

### Orquestación de herramientas existentes

Aquadecomp no reimplementa descompiladores: integra herramientas existentes
(JADX, CFR, Procyon, Vineflower, dex2jar, baksmali/smali, d8 y apksig) en una
única cadena con criterios de parada, respaldo, análisis y verificación. El
valor del proyecto está en esa orquestación: recuperación de las clases que
faltan, análisis de resultados, verificación de la firma y reconstrucción del
APK.

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

### 2.0.0: Análisis y Extensibilidad

- **`rebuild`** - recompila las fuentes descompiladas y genera un APK firmado con los cambios aplicados (firma v1+v2+v3 con certificado propio: instalarlo requiere desinstalar la app previa); detecta `aquedecomp-mapping.txt` para restaurar el classpath y renombrar el dex a los nombres originales (ver [docs/REBUILD.md](docs/REBUILD.md))
- **`--mapping`** - aplica mappings de ProGuard/R8 al descompilar para recuperar clases y miembros con sus nombres originales (ver [docs/REBUILD.md](docs/REBUILD.md))
- **`serve`** - API REST local con JSON para descompilar, inspeccionar, recompilar, analizar nativos e inferir nombres por HTTP (ver [docs/API.md](docs/API.md))
- **`natives`** - análisis ELF real de los `.so` del paquete (secciones, exports, imports, dependencias) con desensamblado Ghidra opcional si está instalado (ver [docs/NATIVES.md](docs/NATIVES.md))
- **Plugins de encriptación** - descifrado de AES/RC4/XOR cuando la clave está embebida como literal, con notas honestas cuando no se puede descifrar (ver [docs/CRYPTO_PLUGINS.md](docs/CRYPTO_PLUGINS.md))
- **`nombres`** - sugerencias heurísticas de nombres para identificadores ofuscados, solo informe, sin modificar código (ver [docs/NAME_INFERENCE.md](docs/NAME_INFERENCE.md))

## Instalación

### Requisitos

- Java 21+
- 4GB RAM mínimo (6GB+ recomendado para APKs grandes)

### Descarga desde releases

Cada release publica el ejecutable `aquedecomp.jar` junto con su checksum
`aquedecomp.jar.sha256`:

```bash
curl -LO https://github.com/Fluxionics/Aquadecomp/releases/latest/download/aquedecomp.jar
curl -LO https://github.com/Fluxionics/Aquadecomp/releases/latest/download/aquedecomp.jar.sha256
```

Verificar la integridad antes de usarlo:

```bash
# Linux / macOS
sha256sum -c aquedecomp.jar.sha256

# Windows (PowerShell)
certutil -hashfile aquedecomp.jar SHA256
```

El hash publicado en el archivo `.sha256` y en la página de la release debe
coincidir con el resultado de `certutil`. Después:

```bash
java -jar aquedecomp.jar --version
```

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

# Descompilar aplicando un mapping de ProGuard/R8
java -jar aquedecomp.jar decompile signal.apk -o ./output --mapping mapping.txt

# Recompilar las fuentes editadas en un APK firmado
java -jar aquedecomp.jar rebuild ./output -o ./output-modificado.apk

# Analizar bibliotecas nativas (.so)
java -jar aquedecomp.jar natives signal.apk --so libsignal

# Sugerir nombres para identificadores ofuscados
java -jar aquedecomp.jar nombres ./output/sources --out ./output/nombres.json

# API REST local
java -jar aquedecomp.jar serve --port 8080
```

### GUI

```bash
java -jar aquedecomp.jar --gui

# Equivalente (aparece en aquedecomp --help)
java -jar aquedecomp.jar gui
```

La ventana ofrece en pestañas las funciones de la CLI: descompilar, analizar
(inspect), recompilar, analizar bibliotecas nativas y sugerir nombres. Los
resultados aparecen en las pestañas Reporte y Registro.

## Estructura del Proyecto

```
src/main/java/com/aquadecomp/
├── Aquadecomp.java               Punto de entrada (CLI o GUI)
├── cli/
│   └── AquaCli.java              Comandos de línea (decompile, inspect, rebuild, natives, nombres, serve, gui)
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
├── rebuild/
│   ├── RebuildPipeline.java      Compilación, dex y firma del APK
│   ├── SourceCompiler.java       javac sobre las fuentes
│   ├── DexBuilder.java           Clases a dex (dexlib2)
│   └── ApkPacker.java            Reemplazo de dex y firma v1+v2+v3
├── mapping/
│   ├── MappingFile.java          Parser ProGuard/R8
│   ├── MappingApplier.java       Aplicación al APK (ZIP en memoria)
│   └── DexMappingRewriter.java   Renombrado del dex recompilado
├── api/
│   └── ApiServer.java            Servidor REST local (HttpServer del JDK)
├── nativecode/
│   ├── ElfParser.java            Cabecera, secciones y símbolos ELF
│   ├── NativeAnalyzer.java       Bibliotecas del paquete
│   ├── GhidraLocator.java        Detección de analyzeHeadless
│   └── GhidraRunner.java         Ejecución headless con script propio
├── crypto/
│   ├── DecryptionPlugin.java     SPI de plugins (ServiceLoader)
│   ├── PluginRegistry.java       Integrados + classpath
│   ├── AesEmbeddedKeyPlugin.java AES con clave literal
│   ├── Rc4EmbeddedKeyPlugin.java RC4 con clave literal
│   └── XorConstantPlugin.java    XOR con constante
├── naming/
│   ├── NameInferer.java          Reglas heurísticas
│   ├── NameSuggestion.java       Una sugerencia
│   └── NameReport.java           Informe JSON
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
  "toolVersion": "2.0.1"
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

La tabla oficial de benchmarks (APK pequeño, mediano y grande, con archivos,
efectividad, confianza y motores por corrida) se publica por versión en
[docs/VERIFICATION.md](docs/VERIFICATION.md); ese es el único documento con
tiempos medidos. Para reproducir estas medidas y las de los cuatro formatos,
sigue el procedimiento de esa guía.

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
- Agregado Procyon como cuarto descompilador de la cadena (JADX, CFR, Procyon, Vineflower)
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

### 2.0.0: Análisis y Extensibilidad (Completado)
- `rebuild`: recompila las fuentes editadas y genera un APK firmado con los cambios
- `--mapping`: mappings de ProGuard/R8 para restaurar nombres al descompilar y volver a ofuscar el dex recompilado
- `serve`: API REST local con endpoints de decompile, inspect, rebuild, natives y names
- `natives`: análisis ELF real de los `.so` con Ghidra headless opcional
- Plugins de encriptación por SPI (`aes-clave-embebida`, `rc4-clave-embebida`, `xor-constante-base64`)
- `nombres`: inferencia heurística de nombres para identificadores ofuscados, solo informe
- Suite ampliada y matriz de verificación de 2.0.0 en `docs/VERIFICATION.md`

### Funciones congeladas

A partir de 2.0.1 las funcionalidades quedan congeladas: no se agregan
motores, algoritmos ni comandos nuevos hasta completar la verificación de
esta versión; solo se aceptan correcciones de errores. Los criterios de
1.0.0, 2.0.0 y 2.0.1 y cómo comprobarlos están en
[docs/VERIFICATION.md](docs/VERIFICATION.md).

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
- Contacto: guille.g.r.s.m@gmail.com
