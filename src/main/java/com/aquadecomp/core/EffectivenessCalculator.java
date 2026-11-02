package com.aquadecomp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EffectivenessCalculator {

    public static final String METHODOLOGY = """
            efectividad = 0.40*Cobertura + 0.25*Integridad + 0.25*Identificadores + 0.10*Recursos \
            (si se omiten recursos, 0.45*Cobertura + 0.30*Integridad + 0.25*Identificadores). \
            Cobertura = clases con código / clases totales. \
            Integridad = 1 - (errores + 0.25*avisos)/clases. \
            Identificadores = 1 - fracción de nombres ofuscados. \
            Recursos = manifiesto decodificado (0.6) + recursos extraídos (0.4). \
            Limitación inevitable: comentarios, nombres de variables locales y \
            sintaxis exacta se pierden en la compilación; ningún descompilador \
            los recupera.""";

    public static EffectivenessCalculator of() {
        return new EffectivenessCalculator();
    }

    public EffectivenessReport calculate(ManifestInfo manifest,
                                         ObfuscationInfo obf,
                                         DecompileStats stats,
                                         boolean resourcesRequested) {
        try {

            double coverage = stats.classesTotal() == 0 ? 0
                    : (double) stats.classesWithCode() / stats.classesTotal();
            String coverageDetail = String.format(Locale.ROOT,
                    "%d de %d clases con código generado (%.1f%%)",
                    stats.classesWithCode(), stats.classesTotal(), coverage * 100);

            double integrity;
            if (stats.classesTotal() == 0) {
                integrity = 0;
            } else {
                double penalty = (stats.errors() + 0.25 * stats.warns()) / stats.classesTotal();
                integrity = Math.max(0, 1 - penalty);
            }
            String integrityDetail = String.format(Locale.ROOT,
                    "%d errores y %d advertencias de descompilación sobre %d clases (%.1f%% limpio)",
                    stats.errors(), stats.warns(), stats.classesTotal(), integrity * 100);

            double identifiers = Math.max(0, 1 - obf.obfuscatedRatio());
            String identifierDetail = String.format(Locale.ROOT,
                    "%.1f%% de nombres recuperados/no ofuscados (nivel de ofuscación: %s)",
                    identifiers * 100, obf.level());

            double resource;
            String resourceDetail;
            if (!resourcesRequested) {
                resource = -1;
                resourceDetail = "No evaluado (recursos omitidos por el usuario)";
            } else {
                double manifestPart = manifest != null && manifest.parsedOk() ? 0.6 : 0.0;
                double resPart = stats.resourcesDecoded() ? 0.4 : 0.0;
                resource = manifestPart + resPart;
                resourceDetail = String.format(Locale.ROOT,
                        "Manifiesto: %s | Recursos extraídos: %s",
                        (manifest != null && manifest.parsedOk()) ? "OK" : "FALLO",
                        stats.resourcesDecoded() ? "OK" : "NO");
            }

            double overall;
            if (resource < 0) {
                overall = 0.45 * coverage + 0.30 * integrity + 0.25 * identifiers;
            } else {
                overall = 0.40 * coverage + 0.25 * integrity
                        + 0.25 * identifiers + 0.10 * resource;
            }
            overall = Math.round(overall * 1000.0) / 10.0;

            List<String> strengths = new ArrayList<>();
            List<String> limitations = new ArrayList<>();

            if (coverage >= 0.95) strengths.add("Casi todas las clases se descompilaron a Java");
            else if (coverage >= 0.75) strengths.add("La mayoría de clases tiene código recuperado");
            else limitations.add("Muchas clases quedaron sin código (fallo del motor o código ofuscado)");

            if (stats.errors() == 0) strengths.add("Sin errores de descompilación");
            else limitations.add(stats.errors() + " errores de descompilación: esas partes del código pueden estar incompletas");

            if (obf.obfuscatedRatio() < 0.05) strengths.add("Nombres de clases/métodos aparentemente originales");
            else limitations.add(String.format(Locale.ROOT,
                    "%.0f%% de los identificadores siguen ofuscados: los nombres "
                            + "recuperados son sustitutos, no los originales",
                    obf.obfuscatedRatio() * 100));

            if (obf.likelyStringEncryption()) {
                limitations.add("Cadenas de texto posiblemente cifradas: los valores "
                        + "reales solo se ven en runtime");
            }
            if (manifest == null || !manifest.parsedOk()) {
                limitations.add("AndroidManifest no se pudo decodificar por completo");
            }

            limitations.add("Los comentarios del código original se pierden siempre "
                    + "(no existen en el bytecode)");
            limitations.add("Los nombres de variables locales no se recuperan "
                    + "(el motor genera var1, var2...)");
            limitations.add("La estructura exacta del código original (azúcar sintáctico, "
                    + "orden de declaraciones) es una reconstrucción, no una copia");

            if (stats.classesTotal() == 0) {
                return EffectivenessReport.unknown("No se cargaron clases del APK");
            }

            return new EffectivenessReport(overall, round1(coverage * 100),
                    round1(integrity * 100), round1(identifiers * 100),
                    resource < 0 ? -1 : round1(resource * 100),
                    coverageDetail, integrityDetail, identifierDetail, resourceDetail,
                    List.copyOf(strengths), List.copyOf(limitations), METHODOLOGY);
        } catch (Exception e) {
            return EffectivenessReport.unknown("Error calculando efectividad: " + e.getMessage());
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private EffectivenessCalculator() {
    }
}
