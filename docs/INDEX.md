# Documentación de Aquadecomp

Aquadecomp es una plataforma multi-herramienta de descompilación de aplicaciones Android: JADX es el motor principal y CFR, Procyon y Vineflower rescatan las clases que falten. El flujo `decompile` ejecuta siete etapas conectadas: normalización de entrada, inspección, manifiesto, descompilación, recuperación de código, puntuación de confianza y cálculo de efectividad (ver [README.md](../README.md)).

## Guías Principales

### Para Usuarios
**Comienza aquí si quieres usar Aquadecomp**

- [Inicio Rápido](../README.md) - Instalación, primer uso, características principales
- [Guía de Uso (USAGE.md)](USAGE.md) - guía completa:
  - Sintaxis de línea de comandos
  - Casos de uso
  - Interpretación de resultados
  - Troubleshooting
  - Scripting
- [Recompilación y mapping (REBUILD.md)](REBUILD.md) - `rebuild` para editar fuentes y regenerar el APK, y `--mapping` para mappings de ProGuard/R8
- [API REST (API.md)](API.md) - comando `serve`, endpoints JSON y ejemplos con curl
- [Bibliotecas nativas (NATIVES.md)](NATIVES.md) - comando `natives`, análisis ELF real y Ghidra opcional
- [Plugins de encriptación (CRYPTO_PLUGINS.md)](CRYPTO_PLUGINS.md) - descifrado de AES/RC4/XOR con clave embebida y cómo escribir un plugin
- [Inferencia de nombres (NAME_INFERENCE.md)](NAME_INFERENCE.md) - comando `nombres`, sugerencias heurísticas para identificadores ofuscados

### Para Desarrolladores
**Comienza aquí si quieres entender o extender Aquadecomp**

- [Arquitectura (ARCHITECTURE.md)](ARCHITECTURE.md) - diseño de capas y paquetes:
  - Input Layer (validación de entrada)
  - Decompilation Layer (cadena de descompiladores)
  - Recovery Layer (motores de recuperación)
  - Verification Layer (puntuación de confianza)
  - Reporting Layer (JSON + Markdown)
  - Paquetes de 2.0.0: `rebuild`, `mapping`, `api`, `nativecode`, `crypto`, `naming`
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
- [Verificación (VERIFICATION.md)](VERIFICATION.md) - criterios de 1.0.0, 2.0.0, 2.0.1 y 2.0.2, comandos reproducibles y matriz de resultados

## Estructura del Proyecto

```
Aquadecomp/
├── README.md                  Visión general y características
├── CONTRIBUTING.md            Cómo contribuir
├── CODE_OF_CONDUCT.md         Código de conducta
├── SECURITY.md                Política de seguridad
├── build.gradle.kts           Configuración Gradle
├── libs/jadx/                 JADX 1.5.6 (motor principal)
├── scripts/                   Regeneración de fixtures (make-fixtures, make-aab)
├── docs/                      Esta documentación
│   ├── INDEX.md
│   ├── ARCHITECTURE.md
│   ├── USAGE.md
│   ├── RECOVERY_ENGINES.md
│   ├── CONFIDENCE_SCORING.md
│   ├── REBUILD.md
│   ├── API.md
│   ├── NATIVES.md
│   ├── CRYPTO_PLUGINS.md
│   ├── NAME_INFERENCE.md
│   └── VERIFICATION.md
├── src/main/java/com/aquadecomp/
│   ├── Aquadecomp.java        Punto de entrada
│   ├── cli/                   Interfaz de línea de comandos
│   ├── gui/                   Interfaz gráfica Swing
│   ├── core/                  Flujo, motor, inspector, reportes
│   ├── decompilation/         Cadena de descompiladores
│   ├── input/                 Validación de entrada
│   ├── recovery/              Motores de recuperación
│   ├── verification/          Puntuación de confianza
│   ├── rebuild/               Recompilación y firma de APK
│   ├── mapping/               Aplicación de mappings ProGuard/R8
│   ├── api/                   Servidor REST local
│   ├── nativecode/            Análisis ELF y Ghidra
│   ├── crypto/                Plugins de descifrado
│   └── naming/                Inferencia de nombres
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
- **2.0.0**: recompilación con `rebuild`, mappings con `--mapping`, API REST, análisis nativo, plugins de encriptación e inferencia de nombres (completado)

Las funciones nuevas están congeladas: no hay motores, algoritmos ni
características agregados hasta una versión posterior. El estado de los
criterios de 1.0.0, 2.0.0, 2.0.1 y 2.0.2 está en [VERIFICATION.md](VERIFICATION.md).
