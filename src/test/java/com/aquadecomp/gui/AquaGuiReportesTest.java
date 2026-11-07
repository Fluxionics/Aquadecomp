package com.aquadecomp.gui;

import com.aquadecomp.core.ApkStructure;
import com.aquadecomp.core.ManifestInfo;
import com.aquadecomp.nativecode.ElfFixture;
import com.aquadecomp.nativecode.ElfParser;
import com.aquadecomp.nativecode.NativeAnalyzer;
import com.aquadecomp.naming.NameReport;
import com.aquadecomp.naming.NameSuggestion;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AquaGuiReportesTest {

    @Test
    void inspeccionIncluyeEstructuraYManifiesto() {
        ApkStructure s = new ApkStructure("app.apk", 1048576, 12,
                List.of("classes.dex"), List.of("lib/arm64-v8a/libx.so"),
                List.of("assets/a.txt"), List.of("ProtectorX"), List.of("aviso de prueba"),
                true, true, false, false);
        ManifestInfo m = new ManifestInfo("com.ejemplo", "1.2", "3", null, false, true,
                List.of("android.permission.INTERNET"), List.of("com.ejemplo.Main"),
                "com.ejemplo.Main", true, null);

        String texto = AquaGui.textoInspeccion(Path.of("app.apk"), "APK", false, s, m);

        assertTrue(texto.contains("=== Analizar APK ==="));
        assertTrue(texto.contains("1.00 MB"));
        assertTrue(texto.contains("DEX:        [classes.dex]"));
        assertTrue(texto.contains("com.ejemplo"));
        assertTrue(texto.contains("android.permission.INTERNET"));
        assertTrue(texto.contains("ProtectorX"));
        assertTrue(texto.contains("aviso de prueba"));
    }

    @Test
    void inspeccionDeManifiestoFallidoLoDice() {
        ApkStructure s = ApkStructure.empty("roto.apk");
        ManifestInfo m = ManifestInfo.failed("XML corrupto");

        String texto = AquaGui.textoInspeccion(Path.of("roto.apk"), "APK", false, s, m);

        assertTrue(texto.contains("Error: XML corrupto"));
        assertTrue(texto.contains("Sin packers conocidos detectados"));
    }

    @Test
    void nativosConBibliotecaRealMuestraDetallesElf() throws Exception {
        ElfParser.ElfInfo elf = ElfParser.parse(ElfFixture.elf64());
        NativeAnalyzer.Library lib =
                new NativeAnalyzer.Library("lib/arm64-v8a/libbar.so", elf.abi(), elf, null);
        NativeAnalyzer.Report report = new NativeAnalyzer.Report(1, List.of(lib),
                List.of(),
                new NativeAnalyzer.GhidraStatus(false, true, "Ghidra no solicitado", null, 0));

        String texto = AquaGui.textoNativos(Path.of("app.apk"), report, 10);

        assertTrue(texto.contains("Bibliotecas: 1"));
        assertTrue(texto.contains("lib/arm64-v8a/libbar.so"));
        assertTrue(texto.contains("64 bits"));
        assertTrue(texto.contains("SONAME:"));
        assertTrue(texto.contains("libfake.so"));
        assertTrue(texto.contains("Necesita:     libbar.so"));
        assertTrue(texto.contains("Primeros exports"));
        assertTrue(texto.contains("-- Ghidra --"));
        assertTrue(texto.contains("Ghidra no solicitado"));
    }

    @Test
    void nativosConBibliotecaCorruptaLoDice() {
        NativeAnalyzer.Library lib = new NativeAnalyzer.Library(
                "lib/armeabi-v7a/libroto.so", null, null, "ELF truncado");
        NativeAnalyzer.Report report = new NativeAnalyzer.Report(1, List.of(lib),
                List.of(),
                new NativeAnalyzer.GhidraStatus(false, true, "Ghidra no solicitado", null, 0));

        String texto = AquaGui.textoNativos(Path.of("app.apk"), report, 10);

        assertTrue(texto.contains("libroto.so"));
        assertTrue(texto.contains("Error: ELF truncado"));
    }

    @Test
    void nombresListaSugerenciasYSusNotas() {
        NameReport r = new NameReport("/fuentes", 3,
                List.of(new NameSuggestion("A.java", "campo", "f1", "contador", 70,
                        List.of("se incrementa"))),
                List.of("nota de proceso"));

        String texto = AquaGui.textoNombres(r, 40);

        assertTrue(texto.contains("=== Inferencia de nombres ==="));
        assertTrue(texto.contains("Sugerencias:  1"));
        assertTrue(texto.contains("[campo] f1 -> contador"));
        assertTrue(texto.contains("señal: se incrementa"));
        assertTrue(texto.contains("Nota: nota de proceso"));
    }

    @Test
    void nombresSinSugerenciasLoDice() {
        NameReport r = new NameReport("/fuentes", 3, List.of(), List.of());

        String texto = AquaGui.textoNombres(r, 40);

        assertTrue(texto.contains("Sugerencias:  0"));
        assertTrue(texto.contains("No se detectaron identificadores"));
    }

    @Test
    void nombresLimitaLasSugerenciasImpresas() {
        List<NameSuggestion> muchas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            muchas.add(new NameSuggestion("A.java", "campo", "f" + i, "c" + i, 50,
                    List.of()));
        }
        NameReport r = new NameReport("/fuentes", 5, muchas, List.of());

        String texto = AquaGui.textoNombres(r, 2);

        assertTrue(texto.contains("... y 3 más"));
    }
}
