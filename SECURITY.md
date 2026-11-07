# Política de Seguridad

## Versiones Soportadas

| Versión | Soportado |
| ------- | --------- |
| 2.x.x   | Sí        |
| 1.x.x   | Solo correcciones críticas de seguridad |

## Qué se Considera un Problema de Seguridad

- Ejecución de código arbitrario al procesar un APK malicioso
- Escritura fuera de las rutas de salida indicadas por la persona
  usuaria
- Filtrado de información sensible en la API REST local
- Firmas o verificación de APK afectadas por el módulo `rebuild`
- Vulnerabilidades en dependencias usadas en tiempo de ejecución

No se consideran problemas de seguridad:

- Limitaciones deprecadas en los motores de descompilación (se
  documentan en cada release)
- El uso previsto de la herramienta: Aquadecomp procesa APK de forma
  local y no sube archivos a ningún servicio
- Falsos positivos en heurísticas de cadenas o inferencia de nombres

## Cómo Reportar una Vulnerabilidad

Reporta la vulnerabilidad de forma privada usando las Security
Advisories del repositorio en GitHub:

https://github.com/Fluxionics/Aquadecomp/security/advisories/new

No abras un issue público antes de que exista una corrección.

Incluye en el reporte:

1. Descripción del problema y su impacto
2. Pasos mínimos para reproducirlo
3. Versión de Aquadecomp y sistema operativo
4. APK de prueba o fragmento afectado, si es aplicable

## Respuesta Esperada

- Acuse de recibo en un plazo máximo de 7 días
- Evaluación y decisión sobre la validez en un plazo máximo de 14 días
- Corrección y release para vulnerabilidades confirmadas, con
  agradecimiento público si la persona que reporta lo desea

## Alcance del Proceso

Este proceso cubre el código de este repositorio y sus dependencias
directas. Los reportes enviados por el proceso privado tendrán
prioridad sobre los reportados en público.
