# Arquitectura de Aquadecomp

## Visión General

Aquadecomp implementa una arquitectura modular de múltiples capas que integra descompiladores en una estrategia de cadena de respaldo (fallback chain), priorizando calidad y cobertura del código decompilado.

## Capas de la Arquitectura

### 1. Capa de Entrada (Input Layer)

**Responsabilidad**: Normalizar diversos formatos de entrada (APK, AAB, XAPK, APKS) a una representación uniforme.

**Componentes**:
- `InputNormalizer`: Detecta tipo de entrada y extrae DEX + recursos
- `InputInspector`: Analiza estructura, detecta packers, librerías nativas

**Formatos soportados**:
```
APK (.apk)     → ZIP con classes.dex, resources
AAB (.aab)     → Bundle con múltiples DEX en módulos
XAPK (.xapk)   → ZIP con APKs + OBB
APKS (.apks)   → ZIP con splits APK
```

**Flujo**:
```
Input File
    ↓
InputNormalizer.detectType()
    ↓
normalize() → normalizeApk() / normalizeAab() / etc.
    ↓
NormalizedInput {
    - dexFiles: List<Path>
    - manifestPath: Path
    - resourcesDir: Path
    - tempDirectory: Path
}
```

### 2. Capa de Descompilación (Decompilation Layer)

**Responsabilidad**: Ejecutar JADX como motor principal y rescatar con otros motores las clases que falten.

**Arquitectura**:

```
DecompilerStrategy (Interface)
    ↓
    ├─ JadxDecompilerWrapper   (motor principal, descompila el APK)
    ├─ Dex2JarConverter       (convierte APK a JAR para los motores de rescate)
    ├─ CfrDecompiler          (rescate)
    ├─ ProcyonDecompiler      (rescate)
    └─ VineflowerDecompiler   (rescate)

DecompilerChain
    ├─ primary: JadxDecompilerWrapper
    ├─ engines: List<DecompilerStrategy>  (CFR, Procyon, Vineflower)
    ├─ decompile(): ChainResult
    │   → Ejecuta JADX
    │   → Detecta clases sin archivo comparando el JAR con sources/
    │   → Rescata solo las clases faltantes con cada motor disponible
    └─ rescue(): ChainResult  (rescate directo de una lista de clases)
```

**Configuración por descompilador**:

```java
DecompilerConfig {
    - deobfuscate: boolean      // Intentar desofuscación
    - includeResources: boolean // Extraer recursos
    - threads: int              // Paralelismo
    - showBadCode: boolean      // Mostrar código problemático
}
```

**Resultado de descompilación**:

```
DecompilationResult {
    - success: boolean
    - sourcesDir: Path          // Código generado
    - resourcesDir: Path        // Recursos extraídos
    - classesDecompiled: int    // Conteo de clases
    - errors: int               // Errores durante proceso
    - message: String           // Error si fracasó
}
```

### 3. Capa de Verificación (Verification Layer)

**Responsabilidad**: Evaluar la calidad del código decompilado y asignar puntuaciones de confianza.

**Componentes**:

- `ConfidenceScorer`: Analiza cada archivo y asigna score
- `ConfidenceMap`: Almacena y agrega datos de confianza
- `ClassConfidence`: Record de confianza de una clase

**Algoritmo de puntuación**:

```
Base Score:
    - JAVA: 75
    - SMALI: 10

Ajustes:
    - JADX WARNING: -15
    - Método no implementado: -20
    - TODO/FIXME: -5
    - Código sintético: -10
    - Errores compilación: -20

Final Score = Math.clamp(base + adjustments, 0, 100)
Level = ConfidenceLevel.fromScore(score)
```

**Niveles de confianza**:

```
100-90: EXCELLENT    ( Excelente)
89-75:  GOOD         ( Bueno)
74-60:  ACCEPTABLE   ( Aceptable)
59-40:  POOR         ( Pobre)
39-25:  UNRELIABLE   ( No confiable)
24-0:   SMALI_FALLBACK ( Solo Smali)
```

### 4. Capa de Orquestación (Orchestration Layer)

**Responsabilidad**: Coordinar todo el flujo de descompilación.

**Componente principal**: `AquadecompOrchestrator`

**Flujo de orquestación**:

```
1. NORMALIZANDO ENTRADA (10%)
   ├─ Detectar formato
   ├─ Extraer DEX + recursos
   └─ Guardar en temporal

2. DESCOMPILANDO (70%)
   ├─ Ejecutar DecompilerChain
   ├─ JADX intento 1 (motor principal)
   ├─ Detectar clases sin archivo
   └─ CFR, Procyon y Vineflower rescatan las clases faltantes

3. CALCULANDO CONFIANZA (20%)
   ├─ Ejecutar ConfidenceScorer
   ├─ Generar ConfidenceMap
   └─ Agregar estadísticas

4. Retornar OrchestrationResult
```

**Opciones de orquestación**:

```java
Options {
    - inputFile: Path
    - outputDir: Path
    - deobfuscate: boolean
    - includeResources: boolean
    - threads: int
    - showBadCode: boolean
}
```

### 5. Capa de Reportes (Reporting Layer)

**Responsabilidad**: Generar reportes en múltiples formatos.

**Componentes**:

- `ComprehensiveReportGenerator`: Genera JSON + Markdown

**Formatos**:

#### JSON Report
```json
{
  "metadata": { project_name, created_at, aquadecomp_version, duration_ms },
  "decompilation": { primary_decompiler, attempts_made, source_directory },
  "statistics": {
    "total_classes": number,
    "average_score": number,
    "confidence_breakdown": { excellent, good, acceptable, poor, unreliable, smali_only },
    "compilable": { count, percentage },
    "decompiler_usage": { JADX: count, ... },
    "format_breakdown": { JAVA: count, SMALI: count }
  }
}
```

#### Markdown Report
- Tabla de confianza
- Gráficos de distribución
- Recomendaciones de calidad

## Modelo de Datos

### ClassConfidence (Verificación)

```java
record ClassConfidence(
    String className,           // Ej: com.signal.MainActivity
    String decompilerUsed,      // "JADX", "Vineflower", etc.
    ConfidenceLevel level,      // EXCELLENT, GOOD, etc.
    int score,                  // 0-100
    List<String> issues,        // ["JADX warning", ...]
    boolean compilesSuccessfully,
    String sourceFormat         // "JAVA", "SMALI"
)
```

### DecompiledProject (Resultado Final)

```java
record DecompiledProject(
    String projectName,
    Path sourceDirectory,
    Path resourcesDirectory,
    Path manifestPath,
    String decompilerUsed,
    ConfidenceMap.ConfidenceStats confidenceStats,
    long durationMillis,
    Instant createdAt,
    String aquadecompVersion,
    List<String> decompilerAttempts
)
```

## Flujos Principales

### Flujo de Descompilación Exitosa

```
User Input (APK)
    ↓ InputNormalizer
Normalized (DEX + Resources)
    ↓ DecompilerChain
    ├─ JadxDecompilerWrapper.decompile()
    │   ├─ Load DEX
    │   ├─ Decompile
    │   ├─ Save sources
    │   └─ Return success
    └─ (CFR, Procyon y Vineflower rescatan clases faltantes vía dex2jar)
    ↓ ConfidenceScorer
    ├─ Iterate each Java file
    ├─ Calculate score
    ├─ Record ClassConfidence
    └─ Generate ConfidenceMap
    ↓ ComprehensiveReportGenerator
    ├─ JSON report
    └─ Markdown report
    ↓ DecompiledProject
Output Directory with all artifacts
```

### Flujo de Rescate de Clases

```
JADX (motor principal)
├─ ¿Generó la clase? → Conservar el archivo
└─ ¿Falta la clase? → dex2jar convierte el APK a JAR
    ├─ CFR intenta rescatarla
    ├─ Procyon intenta rescatarla
    ├─ Vineflower intenta rescatarla
    └─ Si ningún motor la rescató → queda sin archivo (reportada)
```

## Concurrencia y Performance

### Paralelismo

**Nivel 1**: JADX/Decompiladores internos
- Controlado por `--threads` flag
- JADX puede usar `threads` cores internamente

**Nivel 2**: Scoring (futura optimización)
- ConfidenceScorer puede iterar ficheros en paralelo
- Actualmente secuencial

### Consumo de Recursos

```
Memory = Base (500 MB) + InputSize (1x) + Peak (3x APK size)

Ejemplo:
APK 100 MB → 500 + 100 + 300 = 900 MB necesarios
```

## Extensibilidad

### Agregar nuevo Descompilador

```java
// 1. Implementar DecompilerStrategy
public class MyDecompiler implements DecompilerStrategy {
    @Override
    public String name() { return "MyDecompiler"; }
    
    @Override
    public boolean isAvailable() { ... }
    
    @Override
    public Set<String> rescue(Path jar, Path sourcesDir, Set<String> missing,
                              DecompilerConfig config) { ... }
}

// 2. Registrarlo en DecompilerChain
engines.add(new MyDecompiler());
```

### Agregar nueva métrica de confianza

Modificar `ConfidenceScorer.scoreFile()`:
```java
if (content.contains("my_custom_pattern")) {
    score -= 15;
    issues.add("Custom pattern detected");
}
```

## Consideraciones de Diseño

1. **Cadena de Respaldo**: Garantiza cobertura 100%, aunque varíe formato
2. **Confianza por Clase**: Permite identificar qué clases son confiables
3. **Formato Normalizado**: Todos los inputs se normalizan a estructura común
4. **Reportes Detallados**: JSON para máquinas, Markdown para humanos
5. **Modular**: Fácil agregar descompiladores, verificadores, generadores

## Limitaciones Conocidas

1. **Clases faltantes**: Si ningún motor rescata una clase, queda sin archivo en `sources/`
2. **Vineflower/CFR/Procyon**: Pueden no estar disponibles (dependencias opcionales)
3. **Código Nativo**: Librerías .so requieren Ghidra (futuro)
4. **DEX Runtime**: Packers con DEX cargado dinámico no se detectan (futuro)
5. **Renombrado**: Sin ProGuard mapping.txt, no se recuperan nombres originales

## Roadmap de Mejoras

1. **Fase 2.5**: Ghidra headless para .so
2. **Fase 3**: frida-dexdump para packers
3. **Fase 4**: Machine Learning para mejor scoring
4. **Fase 5**: API REST + UI Web mejorada
