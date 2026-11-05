package com.aquadecomp.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ApkInspector {

    private static final Map<String, List<String>> PACKER_SIGNATURES = new LinkedHashMap<>();

    static {
        PACKER_SIGNATURES.put("360 Jiagu (加固)", List.of(
                "libjiagu.so", "libjiagu_a64.so", "libjiagu_art.so",
                "libprotectclass.so", "assets/jiagu", "libstub.so"));
        PACKER_SIGNATURES.put("Tencent Legu (乐固)", List.of(
                "libshella-", "assets/shell-", "com.tencent.stubshell",
                "libshella.so", "txappinfo"));
        PACKER_SIGNATURES.put("Alibaba Jiagu (阿里)", List.of(
                "libsgmain.so", "libsgsecuritybody.so", "libmobisec.so",
                "libslax.so", "libsgmisc.so"));
        PACKER_SIGNATURES.put("iJiami (爱加密)", List.of(
                "libexecmain.so", "libexec.so", "assets/ijiami",
                "libexecvmp.so"));
        PACKER_SIGNATURES.put("Bangcle (梆加固)", List.of(
                "libsecexe.so", "libsecmain.so", "assets/secmain.jar",
                "libsecshell.so", "libsecexe.arm.so"));
        PACKER_SIGNATURES.put("Baidu Jiagu", List.of(
                "libbaiduprotect.so", "libbaiduprotect_x86.so"));
        PACKER_SIGNATURES.put("DexProtector", List.of(
                "libdexhelper.so", "libdexprotector", "assets/.dex"));
        PACKER_SIGNATURES.put("Netqin / Qihoo", List.of(
                "libnqshield.so", "libnqsecshell.so"));
        PACKER_SIGNATURES.put("VirboxProtector", List.of(
                "libvirbox", "libosense", "libslauncher"));
        PACKER_SIGNATURES.put("Obfuscator-LLVM (nativo)", List.of(
                "libtup.so", "libtool-checker.so", "libshell.so"));
        PACKER_SIGNATURES.put("AppSolid", List.of(
                "libapksolid", "assets/.apsolid"));
        PACKER_SIGNATURES.put("JIAGU (genérico)", List.of(
                "libchaosvmp.so", "libtosviolation.so", "libsec.so"));
    }

    public ApkStructure inspect(Path apk) throws IOException {
        String apkPath = apk.toString();
        if (!Files.isRegularFile(apk)) {
            throw new IOException("No existe el archivo: " + apkPath);
        }
        long size = Files.size(apk);

        List<String> dexFiles = new ArrayList<>();
        List<String> nativeLibs = new ArrayList<>();
        List<String> assetEntries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean hasManifest = false;
        boolean hasResourcesTable = false;
        int entryCount = 0;

        try (ZipFile zip = new ZipFile(apk.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                entryCount++;
                String name = e.getName();
                String lower = name.toLowerCase(Locale.ROOT);

                if (lower.startsWith("assets/")) {
                    assetEntries.add(name);
                    if (lower.endsWith(".dex") || lower.endsWith(".jar")) {
                        warnings.add("Ejecutable embebido en assets: " + name);
                    }
                } else if (lower.endsWith(".dex")) {
                    if (!name.contains("/") || lower.contains("/dex/")) {
                        dexFiles.add(name);
                    } else {
                        warnings.add("DEX fuera de la raíz del ZIP: " + name);
                    }
                } else if (lower.startsWith("lib/") && lower.endsWith(".so")) {
                    nativeLibs.add(name);
                } else if (name.equals("AndroidManifest.xml")) {
                    hasManifest = true;
                } else if (name.equals("resources.arsc")) {
                    hasResourcesTable = true;
                }
            }

            List<String> detected = new ArrayList<>();
            List<String> allNames = new ArrayList<>();
            var all = zip.entries();
            while (all.hasMoreElements()) {
                allNames.add(all.nextElement().getName().toLowerCase(Locale.ROOT));
            }
            for (Map.Entry<String, List<String>> sig : PACKER_SIGNATURES.entrySet()) {
                for (String needle : sig.getValue()) {
                    for (String entryName : allNames) {
                        if (entryName.contains(needle)) {
                            detected.add(sig.getKey());
                            break;
                        }
                    }
                    if (detected.contains(sig.getKey())) break;
                }
            }

            boolean multiDex = dexFiles.size() > 1;
            boolean hiddenDex = warnings.stream().anyMatch(w -> w.contains("fuera de la raíz"));
            if (multiDex) warnings.add("Multidex detectado (" + dexFiles.size() + " DEX)");
            if (!hasManifest) warnings.add("Falta AndroidManifest.xml");
            if (!hasResourcesTable) warnings.add("Falta resources.arsc (sin recursos compilados)");
            if (dexFiles.isEmpty() && !hiddenDex) {
                warnings.add("No se encontró classes.dex en la raíz (posible packer)");
            }

            return new ApkStructure(apkPath, size, entryCount,
                    List.copyOf(dexFiles), List.copyOf(nativeLibs),
                    List.copyOf(assetEntries), List.copyOf(detected),
                    List.copyOf(warnings), hasManifest, hasResourcesTable,
                    multiDex, hiddenDex);
        }
    }

    public ManifestInfo readManifest(Path apk) {
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null) {
                return ManifestInfo.failed("El APK no contiene AndroidManifest.xml");
            }
            byte[] data;
            try (InputStream in = zip.getInputStream(entry)) {
                data = in.readAllBytes();
            }
            return AxmlParser.parse(data);
        } catch (IOException e) {
            return ManifestInfo.failed("Error leyendo manifiesto: " + e.getMessage());
        }
    }
}
