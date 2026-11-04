# Sistema de Confianza de Aquadecomp

## Introducción

El sistema de confianza es el diferenciador clave de Aquadecomp. Mientras que otros decompiladores solo generan código, Aquadecomp te dice **qué tan confiable es ese código**.

Cada clase decompilada recibe:
- **Puntuación de 0-100**: Métrica cuantitativa
- **Nivel de confianza**: Categoría cualitativa
- **Lista de problemas**: Detalles específicos
- **Información de compilabilidad**: ¿Compila?

## Metodología

### Fase 1: Puntuación Base

```
JAVA (from JADX/Vineflower/CFR):  75 puntos base
SMALI (from Baksmali):             10 puntos base
```

La lógica: El código JAVA generado por decompiladores es inherentemente mejor que Smali.

### Fase 2: Ajustes por Problemas Detectados

Se escanea el contenido del archivo en busca de patrones de riesgo:

```
Cada problema reduce la puntuación:

| Patrón                                  | Reducción | Razón                          |
|--------------------------------------  |-----------|--------------------------------|
| "// JADX WARNING"                       | -15       | Advertencia JADX explícita     |
| "throw new UnsupportedOperationException" | -20   | Método no implementado         |
| "// TODO" o "// FIXME"                  | -5        | Trabajo incompleto             |
| "/* missing */"                         | -10       | Código faltante                |
| "/* synthetic */"                       | -10       | Código generado automático     |
| "cannot find symbol"                    | -5c/error | Error de compilación           |
| "incompatible types"                    | -5c/error | Tipo incompatible              |
| Encriptación de strings detectada       | -8        | Posible ofuscación extra       |
```

### Fase 3: Fórmula Final

```
Final Score = Math.clamp(base + adjustments, 0, 100)
```

**Ejemplo 1**: Clase JADX limpia
```
Base:       75
Ajustes:    0 (sin problemas)
Final:      75 → GOOD
```

**Ejemplo 2**: Clase JADX con warnings
```
Base:       75
JADX WARNINGs: -15
Final:      60 → ACCEPTABLE
```

**Ejemplo 3**: Clase Baksmali sin problemas
```
Base:       10
Ajustes:    0
Final:      10 → SMALI_FALLBACK
```

## Niveles de Confianza

### 1. EXCELLENT (90-100)

**Características**:
- Descompilador: JADX / Vineflower / CFR
- Sin warnings ni problemas
- Compila perfectamente
- Código limpio

**Uso recomendado**: Análisis de seguridad, refactoring, reutilización

**Proporción típica**: 30-50% del código

```
Ejemplo:
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
}
```

### 2. GOOD (75-89)

**Características**:
- Descompilador: JADX primario
- 1-2 warning menores
- Compila con pocas correcciones
- Funcionalidad clara

**Uso recomendado**: Análisis general, debugging, documentación

**Proporción típica**: 20-35% del código

```
Ejemplo:
// JADX WARNING: something suspicious
public void processData(String data) {
    if (data != null) {
        // handling logic
    }
}
```

### 3. ACCEPTABLE (60-74)

**Características**:
- Descompilador: JADX con varios warnings
- Múltiples problemas menores
- Compila pero requiere ajustes
- Lógica identificable

**Uso recomendado**: Referencia, análisis de flujo, debugging

**Proporción típica**: 15-25% del código

```
Ejemplo:
// JADX WARNING: variable type changed
Object processingResult; // was originally complex type
```

### 4. POOR (40-59)

**Características**:
- Descompilador: CFR / Vineflower
- Métodos no implementados
- Código sintético
- Requiere reescritura

**Uso recomendado**: Referencia de bajo nivel, no recomendado para implementación

**Proporción típica**: 5-15% del código

```
Ejemplo:
// Code is incomplete
public void complexMethod() {
    throw new UnsupportedOperationException("Code not decompiled");
}
```

### 5. UNRELIABLE (25-39)

**Características**:
- Descompilador: Baksmali o CFR con muchos problemas
- Múltiples errores de compilación
- Clase sintética/generada
- Difícil de interpretar

**Uso recomendado**: NO RECOMENDADO - Referencias solo

**Proporción típica**: 1-5% del código

```
Ejemplo:
// Synthetized code - DO NOT USE
public class $Synthetic { /* ? */ }
```

### 6. SMALI_FALLBACK (0-24)

**Características**:
- Formato: Smali puro
- Todos los decompiladores fallaron
- Bytecode sin traducción
- Requiere expertise Smali

**Uso recomendado**: Referencia especializada, análisis de bytecode

**Proporción típica**: 0-2% del código

```
Ejemplo:
.method public someMethod()V
    .registers 2
    const v0, 0x1
    return-void
.end method
```

## Estadísticas Agregadas

### ConfidenceMap

Almacena confianza de todas las clases y permite queries:

```java
// Estadísticas globales
stats.getTotalClasses()          // 5432
stats.getExcellentCount()        // 1890
stats.getGoodCount()             // 1123
stats.getAcceptableCount()       // 1034
stats.getPoorCount()             // 256
stats.getUnreliableCount()       // 89
stats.getSmaliOnlyCount()        // 40

// Porcentajes
stats.getExcellentPercentage()   // 34.8%
stats.getReliablePercentage()    // 76.2% (excellent+good+acceptable)
stats.getCompilablePercentage()  // 81.3%

// Promedio
stats.getAverageScore()          // 74.3

// Por descompilador
stats.getDecompilerStats()
// { "JADX": 5200, "Vineflower": 180, "CFR": 42, "Baksmali": 10 }

// Por formato
stats.getFormatStats()
// { "JAVA": 5392, "SMALI": 40 }
```

## Interpretación de Resultados

### Caso 1: Proyecto de Alta Calidad

```
Average Score: 82.5%
Excellent:     45%
Good:          28%
Acceptable:    20%
Poor:          5%
Unreliable:    1%
Smali Only:    1%

Interpretación:
 Código limpio, poco ofuscado
 93% del código es confiable
 Seguro para análisis detallado
 Adecuado para refactoring
```

### Caso 2: Proyecto Ofuscado

```
Average Score: 52.1%
Excellent:     15%
Good:          20%
Acceptable:    30%
Poor:          20%
Unreliable:    10%
Smali Only:    5%

Interpretación:
 Código altamente ofuscado
 Solo 65% del código es confiable
 Muchos métodos sintetizados
→ Usar principalmente como referencia
→ Enfocarse en el 15% excelente
```

### Caso 3: Proyecto con Protección

```
Average Score: 31.4%
Excellent:     5%
Good:          10%
Acceptable:    15%
Poor:          25%
Unreliable:    20%
Smali Only:    25%

Interpretación:
 Código altamente protegido/empacado
 Cadena de fallback necesaria (muchas clases Smali)
 Requiere análisis de bytecode
→ Considerar Ghidra para código nativo
→ Considerar análisis dinámico (Frida)
```

## Factores que Afectan la Puntuación

### 1. Descompilador Utilizado

```
Efecto por decompilador:
JADX:       75-95% típico (mejor)
Vineflower: 60-80% típico (buen respaldo)
CFR:        50-75% típico (especializado)
Baksmali:   10% máximo (fallback)
```

### 2. Nivel de Ofuscación

```
Sin ofuscación:  80-100% típico
Leve:            65-85%
Moderada:        40-70%
Severa:          20-50%
Packed/Obfuscado: <30%
```

### 3. Patrones de Código

```
Código limpio:    +20% vs base
Métodos sintéticos: -20%
Código generado:   -15%
Encriptación:      -8%
```

## Mejora de Confianza (Futuro)

### Machine Learning (Fase 4)

Modelo entrenado para detectar:
- Patrones de ofuscación
- Código generado vs escrito a mano
- Confiabilidad basada en contexto

### Mapping Externo (Próximo)

Si tienes `mapping.txt` de ProGuard/R8:
```
Original → Ofuscado
com.signal.MainActivity → a.b.c
```

Esto aumentaría confianza significativamente.

### Análisis Dinámico (Futuro)

Ejecutar APK con Frida para:
- Verificar comportamiento real vs descompilado
- Detectar código cargado dinámicamente
- Confirmar confianza

## Reportes y Exportación

### JSON Export

```json
{
  "statistics": {
    "confidence_breakdown": {
      "excellent": 1890,
      "good": 1123,
      "acceptable": 1034,
      "poor": 256,
      "unreliable": 89,
      "smali_only": 40
    },
    "average_score": 74.3,
    "compilable": {
      "count": 4521,
      "percentage": "83.2%"
    }
  }
}
```

### Markdown Report

Incluye tabla con:
- Breakdown de confianza
- Porcentajes
- Estadísticas de compilabilidad
- Recomendaciones por nivel

## Best Practices

1. **Prioriza EXCELLENT y GOOD**: Usa estos para análisis serio
2. **ACCEPTABLE para referencia**: Verifica lógica antes de usarla
3. **POOR como última opción**: Posiblemente incorrecto
4. **Ignora SMALI_FALLBACK**: Solo si necesitas bytecode puro
5. **Revisa issues**: La lista de problemas explica por qué baja puntuación
6. **Verificación cruzada**: Si score bajo, intenta con Baksmali para comparar

## Limitaciones del Sistema

1. **No detecta lógica incorrecta**: Si el código decompila pero hace algo diferente
2. **No valida semántica**: Solo detecta patrones de riesgo sintáctico
3. **Baksmali siempre 10**: Aunque el Smali sea perfecto
4. **No usa AST completo**: Análisis basado en strings (más rápido pero menos preciso)

## Futuras Mejoras

- [ ] Análisis AST completo para mejor precision
- [ ] Machine Learning para patrones complejos
- [ ] Integración con análisis de strings encriptados
- [ ] Soporte para decompiladores especializados por tipo de código
- [ ] Validación de compilación real (no solo heurística)
