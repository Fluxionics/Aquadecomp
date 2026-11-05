# Documentación de Aquadecomp

Aquadecomp es una plataforma multi-herramienta de descompilación de aplicaciones Android: JADX es el motor principal y CFR, Procyon y Vineflower rescatan las clases que falten. El flujo `decompile` ejecuta siete etapas conectadas: normalización de entrada, inspección, manifiesto, descompilación, recuperación de código, puntuación de confianza y cálculo de efectividad (ver [README.md](../README.md)).

## Guías Principales

### Para Usuarios
**Comienza aquí si quieres usar Aquadecomp**

- [Inicio Rápido](../README.md) - Instalación, primer uso, características principales
- [Guía Rápida (QUICK_START.md)](../QUICK_START.md) - primeros pasos en la terminal
- [Guía de Uso (USAGE.md)](USAGE.md) - guía completa:
  - Sintaxis de línea de comandos
  - Casos de uso
  - Interpretación de resultados
  - Troubleshooting
  - Scripting

### Para Desarrolladores
**Comienza aquí si quieres entender o extender Aquadecomp**

- [Arquitectura (ARCHITECTURE.md)](ARCHITECTURE.md) - diseño de capas:
  - Input Layer (validación de entrada)
  - Decompilation Layer (cadena de descompiladores)
  - Recovery Layer (motores de recuperación)
  - Verification Layer (puntuación de confianza)
  - Reporting Layer (JSON + Markdown)
- [Motores de Recuperación (RECOVERY_ENGINES.md)](RECOVERY_ENGINES.md) - paquete `recovery`:
  - RecoveryPipeline (orquestación)
  - StringDecryptionEngine
  - ClassRecoveryEngine
  - MetadataExtractor
  - RealCompilationVerifier
- [Sistema de Confianza (CONFIDENCE_SCORING.md)](CONFIDENCE_SCORING.md) - metodología de puntuación por clase:
  - Confianza global
  - Verificación de sintaxis
  - Detección de código sintético
  - Niveles (Excelente/Bueno/Aceptable/Pobre/No confiable)
- [Verificación (VERIFICATION.md)](VERIFICATION.md) - criterios de 1.0.0, comandos reproducibles y matriz de resultados

## Estructura del Proyecto

```
Aquadecomp/
├── README.md                  Visión general y características
├── QUICK_START.md             Primeros pasos
├── build.gradle.kts           Configuración Gradle
├── libs/jadx/                 JADX 1.5.6 (motor principal)
├── scripts/                   Regeneración de fixtures (make-fixtures, make-aab)
├── docs/                      Esta documentación
│   ├── INDEX.md
│   ├── ARCHITECTURE.md
│   ├── USAGE.md
│   ├── RECOVERY_ENGINES.md
│   ├── CONFIDENCE_SCORING.md
│   └── VERIFICATION.md
├── src/main/java/com/aquadecomp/
│   ├── Aquadecomp.java        Punto de entrada
│   ├── cli/                   Interfaz de línea de comandos
│   ├── gui/                   Interfaz gráfica Swing
│   ├── core/                  Flujo, motor, inspector, reportes
│   ├── decompilation/         Cadena de descompiladores
│   ├── input/                 Validación de entrada
│   ├── recovery/              Motores de recuperación
│   └── verification/          Puntuación de confianza
└── src/test/java/com/aquadecomp/  Pruebas unitarias
```

## Flujo Principal

```
APK/AAB/XAPK/APKS
    │
    ▼
[1] NORMALIZANDO ENTRADA ──── valida formato y detecta tipo
    │
    ▼
[2] INSPECCIONANDO APK ────── estructura ZIP, DEX, libs, packers
    │
    ▼
[3] DECODIFICANDO MANIFIESTO  paquete, versión, permisos, actividad
    │
    ▼
[4] DESCOMPILANDO A JAVA ──── JADX + rescate con CFR/Procyon/Vineflower
    │
    ▼
[5] RECUPERANDO CÓDIGO ────── cadenas, clases, metadatos, muestra javac
    │
    ▼
[6] PUNTUANDO CONFIANZA ───── puntaje 0-100 por clase
    │
    ▼
[7] CALCULANDO EFECTIVIDAD ── puntaje global + reportes JSON/Markdown
```

## Roadmap

- **Fase 1**: Motor principal y CLI (completado)
- **Fase 2**: Cadena de respaldo multi-motor (completado)
- **Fase 3**: Recuperación y confianza conectadas al flujo (completado)
- **1.0.0**: estabilización, pruebas automatizadas y verificación de los cuatro formatos (completado)

Las funciones nuevas están congeladas: no hay motores, algoritmos ni
características agregados hasta una versión posterior. El estado de los siete
criterios de 1.0.0 está en [VERIFICATION.md](VERIFICATION.md).
