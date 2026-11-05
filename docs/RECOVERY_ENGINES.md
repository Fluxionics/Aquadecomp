# Motores de Recuperación

## Descripción

Después de descompilar, Aquadecomp ejecuta la etapa "RECUPERANDO CÓDIGO" sobre todos los archivos de `sources/`. El paquete `recovery` analiza cada archivo `.java`, aplica transformaciones seguras, detecta metadatos y verifica una muestra con `javac` real. Los resultados alimentan la puntuación de confianza y los reportes.

La recuperación se ejecuta siempre en cada descompilación. No existe una bandera para desactivarla.

## Arquitectura del Pipeline

```
sources/ (.java)
    ↓
StringDecryptionEngine      pliega literales constantes
    ↓
ClassRecoveryEngine         detecta clases internas, anónimas y vacías
    ↓
MetadataExtractor           detecta metadatos Kotlin, anotaciones, origen
    ↓
RealCompilationVerifier     verifica una muestra con javac (con tiempo límite)
    ↓
RecoveryResult + RecoveryMetrics por archivo
```

Orquestado por `RecoveryPipeline`, que recibe el directorio `sources/` y un `PipelineListener` para reportar progreso en la etapa 97%.

## Motores

### 1. StringDecryptionEngine

**Propósito**: convertir literales constantes en cadenas legibles sin alterar el resto del código.

**Transformaciones (únicas)**:
- `new String(new byte[]{72,105,33})` → `"Hi!"`
- `new String(Base64.decode("aGVsbG8=", 0))` → `"hello"`
- XOR dentro del array: `new byte[]{(byte)(65 ^ 32), 66}` → `"aB"`

**Reglas de seguridad**:
- Solo se pliega si el resultado es texto ASCII imprimible (32-126); si no, el código queda intacto
- Se evalúan literales decimales y hexadecimales; cualquier otra expresión deja el código sin cambios
- El mapa de cadenas es local a cada llamada: no hay estado entre archivos
- Los `byte[]` sueltos (sin envolver en `new String(...)`) nunca se tocan

**Verificado con pruebas automatizadas** (`StringDecryptionEngineTest` en `gradle test`): cadenas, Base64, XOR y bytes no imprimibles se comportan como se describe.

### 2. ClassRecoveryEngine

**Propósito**: detectar estructura de clases en el código fuente para alimentar las métricas.

**Análisis**:
- Clases internas: nombres con `$` y su clase externa
- Clases anónimas: patrones `new X() { ... }`
- Clases vacías: cuerpos `{}` o `throw new RuntimeException`

**Nota**: es un motor de análisis (no modifica el código). Los conteos aparecen en `recovery.innerClasses` y en el reporte.

### 3. MetadataExtractor

**Propósito**: detectar metadatos presentes en el código fuente.

**Extracción**:
- Anotaciones: `@Override`, `@Deprecated`, personalizadas
- Metadatos Kotlin: `@Metadata(...)`
- Archivo de origen: `SourceFile = "..."`

**Nota**: también es de análisis. Los archivos con metadatos Kotlin se cuentan en `recovery.kotlinClasses`.

### 4. RealCompilationVerifier

**Propósito**: verificar sintaxis con el compilador `javac` real, no con heurísticas.

**Proceso**:
1. Comprueba disponibilidad con `javac -version` (si no está en el PATH, la muestra se omite)
2. Compila cada archivo seleccionado en un directorio temporal con tiempo límite de 8 segundos (`destroyForcibly` si lo excede)
3. Clifica cada error:
   - **Resolución** (`cannot find symbol`, `does not exist`, `no existe`, ...): se ignoran, porque sin `android.jar` son esperados
   - **Sintaxis** (el resto): cuentan como error reales
4. Puntaje: 100 sin errores de sintaxis; -7 por `incompatible types`, -5 por otros; mínimo 0
5. Limpia el directorio temporal siempre

**Muestreo**: se verifica un archivo cada `total/40` (máximo 40 por ejecución) más todo archivo modificado por la recuperación. Los resultados quedan en `recovery.javacSampled` y `recovery.javacCompiled`.

### 5. RecoveryPipeline

**Propósito**: orquestar los motores sobre todos los archivos `.java` de `sources/`.

**Proceso**:
1. Lista y ordena los archivos
2. Por archivo: aplica StringDecryptionEngine, analiza clases y metadatos
3. Si el archivo cambió, guarda una copia `.bak` junto al original antes de escribir
4. Verifica con javac según el muestreo
5. Reporta progreso cada 500 archivos

**Resultado** (`RecoveryResult`):
- `filesProcessed`, `filesModified`
- `javacSampled`, `javacCompiled`
- `stringsDecrypted`, `kotlinClasses`, `innerClasses`
- `items` (ej.: `{"Cadenas descifradas": N}`)
- `fileMetrics` por archivo (puntaje javac, si fue modificado)
- `durationMs`

Este resultado se serializa en el JSON como `recovery` (`PipelineResult.RecoveryReport`).

## Integración con ConfidenceScorer

`ConfidenceScorer.scoreAll` recibe `Map<String, RecoveryMetrics>`:

```java
baseScore = 75
score += recoveryMetrics.compilationScore() / 10     // si el archivo fue muestreado
score += syntheticAnalysis.scoreAdjustment()
score += compilationVerifier.scoreAdjustment()       // verificación estructural heurística
score -= warningsAndErrors                           // JADX warning, TODO/FIXME, etc.
score = clamp(score, 0, 100)
level = ConfidenceLevel.fromScore(score)
```

## Limitaciones

- **Cifrado complejo**: AES/RSA con claves propias no se descifra; el código queda como está
- **Nombres ofuscados**: sin `mapping.txt` de ProGuard/R8 no se recuperan nombres originales; JADX no recupera nombres reales y su renombrado sintético no se cuenta como recuperado
- **Sintaxis vs. resolución**: sin `android.jar`, muchos errores de compilación son de resolución y se ignoran por diseño
- **Muestra**: solo una parte de los archivos se compila con `javac`; el resto se puntúa con heurísticas

## Ejecución en el Flujo

```java
// En Pipeline.run(), etapa 5
RecoveryPipeline recovery = new RecoveryPipeline();
RecoveryPipeline.RecoveryResult rec = recovery.recover(output.sourcesDir(), listener);

// Etapa 6
ConfidenceScorer scorer = new ConfidenceScorer();
scorer.scoreAll("JADX", output.sourcesDir(), rec.fileMetrics(), listener);
```

## Ver También

- [CONFIDENCE_SCORING.md](CONFIDENCE_SCORING.md) - Cómo se calculan los puntajes
- [ARCHITECTURE.md](ARCHITECTURE.md) - Diseño general del sistema
- [USAGE.md](USAGE.md) - Uso de la línea de comandos
