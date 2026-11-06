package com.aquadecomp.rebuild;

import com.aquadecomp.core.PipelineListener;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;

public final class AndroidJar {

    private static final String DOWNLOAD_URL =
            "https://repo1.maven.org/maven2/org/robolectric/android-all/"
                    + "14-robolectric-10818077/android-all-14-robolectric-10818077.jar";
    private static final String EXPECTED_SHA256 =
            "6be2218c6a53fe3c57bc22ebdc723edcb7270a8a6f187545708aa5c0ed813977";
    private static final String ENV_VAR = "AQUADECOMP_ANDROID_JAR";

    private AndroidJar() {
    }

    public static Path resolve(Path explicit, PipelineListener listener) throws IOException {
        if (explicit != null) {
            if (!Files.isRegularFile(explicit)) {
                throw new IOException("No existe el android.jar indicado: " + explicit);
            }
            return explicit.toAbsolutePath().normalize();
        }

        String env = System.getenv(ENV_VAR);
        if (env != null && !env.isBlank()) {
            Path fromEnv = Path.of(env);
            if (Files.isRegularFile(fromEnv)) {
                return fromEnv.toAbsolutePath().normalize();
            }
            listener.onLog("Aviso: la variable " + ENV_VAR + " apunta a un archivo inexistente: " + env);
        }

        Path cache = cacheDir().resolve("android.jar");
        if (Files.isRegularFile(cache) && Files.size(cache) > 0) {
            return cache;
        }

        return download(cache, listener);
    }

    public static Path bestEffort(Path explicit, PipelineListener listener) {
        try {
            return resolve(explicit, listener);
        } catch (IOException e) {
            listener.onLog("Aviso: android.jar no disponible (" + e.getMessage()
                    + "); se compilara solo contra las clases originales del APK");
            return null;
        }
    }

    private static Path download(Path target, PipelineListener listener) throws IOException {
        listener.onLog("android.jar no esta en caché; se descargara una sola vez (131 MB)");
        Files.createDirectories(target.getParent());
        Path partial = target.resolveSibling("android.jar.part");
        HttpURLConnection conn = (HttpURLConnection) new URL(DOWNLOAD_URL).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        long total = conn.getContentLengthLong();
        long copied = 0;
        int lastPercent = -1;
        try (InputStream in = conn.getInputStream()) {
            try (var out = Files.newOutputStream(partial)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                    copied += read;
                    if (total > 0) {
                        int percent = (int) (copied * 100 / total);
                        if (percent / 10 != lastPercent / 10) {
                            lastPercent = percent;
                            listener.onProgress(Math.min(percent, 99),
                                    "Descargando android.jar: " + percent + "%");
                        }
                    }
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(partial);
            throw new IOException("No se pudo descargar android.jar desde " + DOWNLOAD_URL
                    + ": " + e.getMessage()
                    + ". Puedes indicar un android.jar local con --android-jar o con la variable "
                    + ENV_VAR, e);
        }

        String hash = sha256(partial);
        if (!EXPECTED_SHA256.equals(hash)) {
            Files.deleteIfExists(partial);
            throw new IOException("El android.jar descargado no coincide con el hash esperado ("
                    + hash + ")");
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        listener.onLog("android.jar guardado en caché: " + target);
        return target;
    }

    private static Path cacheDir() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".aquadecomp");
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
