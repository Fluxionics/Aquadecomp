package com.aquadecomp.rebuild;

import com.android.apksig.ApkSigner;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

public final class ApkSignerTool {

    private ApkSignerTool() {
    }

    public static void sign(Path inputApk, Path outputApk, int fallbackMinApi) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X509Certificate certificate = selfSigned(keyPair);

        ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder(
                "aquadecomp", keyPair.getPrivate(), List.of(certificate)).build();

        try {
            sign(inputApk, outputApk, config, null);
        } catch (Exception e) {
            if (e.getMessage() == null
                    || !e.getMessage().contains("minimum supported Android platform")) {
                throw e;
            }
            sign(inputApk, outputApk, config, fallbackMinApi);
        }

        if (!Files.isRegularFile(outputApk)) {
            throw new IOException("El firmador no produjo el APK: " + outputApk);
        }
    }

    private static void sign(Path inputApk, Path outputApk, ApkSigner.SignerConfig config,
                             Integer minSdk) throws Exception {
        ApkSigner.Builder builder = new ApkSigner.Builder(List.of(config))
                .setInputApk(inputApk.toFile())
                .setOutputApk(outputApk.toFile())
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true);
        if (minSdk != null) {
            builder.setMinSdkVersion(minSdk);
        }
        builder.build().sign();
    }

    private static X509Certificate selfSigned(KeyPair keyPair) throws Exception {
        X500Name subject = new X500Name("CN=Aquadecomp Rebuild, O=Aquadecomp");
        Date notBefore = new Date(System.currentTimeMillis() - 86_400_000L);
        Date notAfter = new Date(System.currentTimeMillis() + 3_153_600_000_000L);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(System.currentTimeMillis()),
                notBefore, notAfter, subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
