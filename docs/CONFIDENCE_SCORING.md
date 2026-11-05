# Sistema de Confianza de Aquadecomp

## Introducción

El sistema de confianza indica qué tan fiable es cada clase decompilada. La etapa "PUNTUANDO CONFIANZA" del flujo ejecuta `ConfidenceScorer` sobre todos los archivos `.java` de `sources/`.

Cada clase recibe:
- **Puntuación de 0-100**: métrica cuantitativa
- **Nivel de confianza**: categoría cualitativa
- **Lista de problemas**: detalles que explican la puntuación
- **Estructura válida**: heurística booleana (no es una compilación real)

Los resultados se agregan en `ConfidenceMap` y se publican en el JSON como `confidence` y en el Markdown en la sección "Confianza por clase".

## Metodología

### Fase 1: Puntuación Base

```
Base: 75 puntos
```

### Fase 2: Ajustes

**Del resultado de recuperación** (si el archivo fue muestreado con javac):

```
+ compilationScore / 10        (0 a +10)
```

**Análisis de código sintético** (`SyntheticCodeDetector`):

```
Patrones detectados: métodos bridge, lambdas sintéticas,
código generado por Dagger/Retrofit, código de protección,
clases vacías
```

**Verificación estructural** (`CompilationVerifier`, heurística basada en el texto):

```
Adaptaciones según errores detectados en el archivo
```

**Patrones en el contenido**:

| Patrón                                    | Reducción | Razón                        |
|-------------------------------------------|-----------|------------------------------|
| `// JADX WARNING`                         | -15       | Advertencia JADX explícita   |
| `throw new UnsupportedOperationException`   | -20       | Método no implementado       |
| `// TODO` o `// FIXME`                    | -5        | Trabajo incompleto           |
| `/* missing */` o `/* synthetic */`       | -10       | Código faltante o generado   |
| Patrones de error de compilación en texto | -5 a -20  | Según cantidad detectada     |

**Motor que generó el archivo**: se registra como metadato (`JADX`, `CFR`,
`Procyon`, `Vineflower`) y no altera el puntaje. Un archivo rescatado recibe el
mismo tratamiento evidencial que uno generado por el motor principal.

### Fase 3: Fórmula Final

```
Final Score = clamp(base + ajustes, 0, 100)
Level = ConfidenceLevel.fromScore(score)
```

**Ejemplo 1**: clase limpia de JADX
```
Base:     75
Ajustes:  0
Final:    75 → Bueno
```

**Ejemplo 2**: clase con warning de JADX
```
Base:     75
Ajustes:  -15 (JADX WARNING)
Final:    60 → Aceptable
```

**Ejemplo 3**: clase con muestra javac sin errores de sintaxis
```
Base:     75
Ajustes:  +10 (compilación sin errores de sintaxis)
Final:    85 → Bueno
```

## Niveles de Confianza

| Nivel | Rango | Significado |
|-------|-------|-------------|
| Excelente (EXCELLENT) | 90-100 | Sin problemas detectados; uso general |
| Bueno (GOOD) | 75-89 | Problemas menores; confiable con revisión puntual |
| Aceptable (ACCEPTABLE) | 60-74 | Útil como referencia; verificar la lógica |
| Pobre (POOR) | 40-59 | Métodos no implementados o código sintético; reescritura probable |
| No confiable (UNRELIABLE) | 0-39 | No usar como referencia de implementación |

## Ejemplos de Interpretación

### Caso 1: Proyecto limpio

```
Puntaje medio: 82.5
Excelente: 45%   Bueno: 28%   Aceptable: 20%
Pobre: 5%        No confiable: 1%

→ Código poco ofuscado, la mayoría es confiable
```

### Caso 2: Proyecto ofuscado

```
Puntaje medio: 52.1
Excelente: 15%   Bueno: 20%   Aceptable: 30%
Pobre: 20%       No confiable: 10%

→ Usar principalmente como referencia
→ Enfocarse en las clases Excelente/Bueno
```

### Caso 3: Proyecto con protección

```
Puntaje medio: 31.4
Excelente: 5%    Bueno: 10%   Aceptable: 15%
Pobre: 25%       No confiable: 45%

→ Empacado o con ofuscación severa
→ Revisar la etapa de descompilación y los avisos de packers
```

## Estadísticas Agregadas

`ConfidenceMap` almacena la confianza de todas las clases:

```java
ConfidenceStats stats = map.getSummary();

stats.total()                 // total de clases puntuadas
stats.excellent()             // conteo por nivel
stats.good()
stats.acceptable()
stats.poor()
stats.unreliable()
stats.averageScore()          // puntaje medio
stats.structurallyValid()     // clases que pasan la heurística de estructura
stats.getReliablePercentage() // excelente + bueno + aceptable
stats.getDecompilerStats()    // { "JADX": 6425 }
```

En el JSON:

```json
"confidence": {
  "total": 6425,
  "excellent": 812,
  "good": 2410,
  "acceptable": 1988,
  "poor": 955,
  "unreliable": 260,
  "averageScore": 68.0,
  "structurallyValid": 6146,
  "decompilerStats": { "JADX": 6425 }
}
```

(Valores de ejemplo de una ejecución real con `mini.apk`.)

## Factores que Afectan la Puntuación

### Nivel de ofuscación

```
Sin ofuscación:   puntuaciones altas
Leve:             mayoría en Bueno/Aceptable
Moderada:         mezcla con Pobre
Severa/packeada:  predominio de No confiable
```

### Motor utilizado

JADX es el motor principal; CFR, Procyon y Vineflower solo rescatan clases que
JADX dejó sin archivo. Cada archivo conserva la etiqueta del motor que lo
produjo (`decompiler` en el JSON, `decompilerStats` en el resumen), de modo que
el informe dice de dónde sale cada línea de código. La etiqueta no mueve el
puntaje: el puntaje depende de la evidencia encontrada en el propio archivo.

## Uso Recomendado

1. **Prioriza Excelente y Bueno** para análisis detallado
2. **Aceptable como referencia**: verifica la lógica antes de usarla
3. **Pobre/No confiable**: posible reescritura; contrasta con el bytecode
4. **Revisa la lista de `issues`**: explica por qué bajó la puntuación
5. **Mira `structurallyValid`**: es una heurística, no una compilación real

## Limitaciones del Sistema

1. **No detecta lógica incorrecta**: si el código decompilado hace algo distinto al original
2. **No valida semántica**: solo patrones de riesgo en el texto
3. **Análisis basado en cadenas**: más rápido que un AST completo, menos preciso
4. **`structurallyValid` no es compilación**: es una heurística; la compilación real solo se hace en la muestra javac de la etapa de recuperación
5. **Nombres sintéticos de JADX**: el renombrado de JADX no se cuenta como recuperación de nombres

## Futuras Mejoras

- [ ] Análisis AST completo para mayor precisión
- [ ] Soporte para `mapping.txt` de ProGuard/R8
- [ ] Integración con análisis dinámico
- [ ] Machine Learning para patrones complejos

## Ver También

- [RECOVERY_ENGINES.md](RECOVERY_ENGINES.md) - Motores que alimentan la puntuación
- [ARCHITECTURE.md](ARCHITECTURE.md) - Diseño general del sistema
- [USAGE.md](USAGE.md) - Uso de la línea de comandos
