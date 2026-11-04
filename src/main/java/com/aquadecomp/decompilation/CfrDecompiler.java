package com.aquadecomp.decompilation;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class CfrDecompiler implements DecompilerStrategy {

    private static final String NAME = "CFR";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.benf.cfr.reader.api.CfrDriver");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public Set<String> rescue(Path jar, Path sourcesDir, Set<String> missing, DecompilerConfig config)
            throws Exception {
        Files.createDirectories(sourcesDir);
        JarFileSource source = new JarFileSource(jar);
        try {
            List<String> paths = new ArrayList<>();
            for (String name : missing) {
                String path = name.replace('.', '/') + ".class";
                if (source.contains(path)) {
                    paths.add(path);
                }
            }
            if (paths.isEmpty()) {
                return Set.of();
            }
            FileSink sink = new FileSink(sourcesDir);
            CfrDriver driver = new CfrDriver.Builder()
                    .withClassFileSource(source)
                    .withOutputSink(sink)
                    .withOptions(Map.of())
                    .build();
            driver.analyse(paths);
            return sink.rescued();
        } finally {
            source.close();
        }
    }

    private static final class JarFileSource implements ClassFileSource {

        private final ZipFile zip;
        private final Set<String> entries = new LinkedHashSet<>();

        JarFileSource(Path jar) throws IOException {
            this.zip = new ZipFile(jar.toFile());
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    entries.add(entry.getName());
                }
            }
        }

        boolean contains(String path) {
            return entries.contains(path);
        }

        @Override
        public void informAnalysisRelativePathDetail(String path, String classFilePath) {
        }

        @Override
        public Collection<String> addJar(String jarPath) {
            return entries;
        }

        @Override
        public String getPossiblyRenamedPath(String path) {
            return path;
        }

        @Override
        public Pair<byte[], String> getClassFileContent(String path) throws IOException {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return Pair.make(in.readAllBytes(), path);
            }
        }

        void close() throws IOException {
            zip.close();
        }
    }

    private static final class FileSink implements OutputSinkFactory {

        private final Path dir;
        private final Set<String> rescued = new LinkedHashSet<>();

        FileSink(Path dir) {
            this.dir = dir;
        }

        Set<String> rescued() {
            return rescued;
        }

        @Override
        public List<SinkClass> getSupportedSinks(SinkType type, Collection<SinkClass> available) {
            if (type == SinkType.JAVA) {
                return Collections.singletonList(SinkClass.DECOMPILED);
            }
            if (type == SinkType.EXCEPTION) {
                return Collections.singletonList(SinkClass.EXCEPTION_MESSAGE);
            }
            return Collections.singletonList(SinkClass.STRING);
        }

        @Override
        public <T> Sink<T> getSink(SinkType type, SinkClass sinkClass) {
            if (type == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                return payload -> {
                    SinkReturns.Decompiled decompiled = (SinkReturns.Decompiled) payload;
                    writeClass(decompiled.getPackageName(), decompiled.getClassName(),
                            decompiled.getJava());
                };
            }
            return payload -> {
            };
        }

        private void writeClass(String pkg, String cls, String java) {
            try {
                Path file = dir.resolve(pkg.replace('.', '/')).resolve(cls + ".java");
                Files.createDirectories(file.getParent());
                Files.writeString(file, java, StandardCharsets.UTF_8);
                rescued.add(pkg.isEmpty() ? cls : pkg + "." + cls);
            } catch (IOException ignored) {
            }
        }
    }
}
