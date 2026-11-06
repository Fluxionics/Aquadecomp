package com.aquadecomp.mapping;

import com.aquadecomp.mapping.MappingFile.RenameTable;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.rewriter.DexRewriter;
import org.jf.dexlib2.rewriter.FieldReferenceRewriter;
import org.jf.dexlib2.rewriter.MethodReferenceRewriter;
import org.jf.dexlib2.rewriter.Rewriter;
import org.jf.dexlib2.rewriter.RewriterModule;
import org.jf.dexlib2.rewriter.Rewriters;
import org.jf.dexlib2.writer.io.MemoryDataStore;
import org.jf.dexlib2.writer.pool.DexPool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class DexMappingRewriter {

    public record Result(byte[] dex, int classes, int members) {
    }

    private DexMappingRewriter() {
    }

    public static Result rewrite(byte[] dexBytes, RenameTable table) throws IOException {
        Path temp = Files.createTempFile("aquadecomp-mapping", ".dex");
        try {
            Files.write(temp, dexBytes);
            DexFile dex = DexFileFactory.loadDexFile(temp.toFile(), opcodesFor(dexBytes));
            int[] counts = countRenames(dex, table);
            DexFile rewritten = new DexRewriter(module(table))
                    .getDexFileRewriter().rewrite(dex);
            MemoryDataStore store = new MemoryDataStore();
            DexPool.writeTo(store, rewritten);
            return new Result(Arrays.copyOf(store.getBuffer(), store.getSize()),
                    counts[0], counts[1]);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static RewriterModule module(RenameTable table) {
        return new RewriterModule() {
            @Override
            public Rewriter<String> getTypeRewriter(Rewriters rewriters) {
                return type -> {
                    String renamed = table.types().get(type);
                    return renamed != null ? renamed : type;
                };
            }

            @Override
            public Rewriter<MethodReference> getMethodReferenceRewriter(Rewriters rewriters) {
                Rewriter<MethodReference> base = new MethodReferenceRewriter(rewriters);
                return reference -> {
                    String renamed = table.methodName(reference.getDefiningClass(),
                            reference.getName(), reference.getParameterTypes(),
                            reference.getReturnType());
                    MethodReference rewritten = base.rewrite(reference);
                    if (renamed.equals(reference.getName())) {
                        return rewritten;
                    }
                    return new ImmutableMethodReference(rewritten.getDefiningClass(), renamed,
                            rewritten.getParameterTypes(), rewritten.getReturnType());
                };
            }

            @Override
            public Rewriter<FieldReference> getFieldReferenceRewriter(Rewriters rewriters) {
                Rewriter<FieldReference> base = new FieldReferenceRewriter(rewriters);
                return reference -> {
                    String renamed = table.fieldName(reference.getDefiningClass(),
                            reference.getName(), reference.getType());
                    FieldReference rewritten = base.rewrite(reference);
                    if (renamed.equals(reference.getName())) {
                        return rewritten;
                    }
                    return new ImmutableFieldReference(rewritten.getDefiningClass(), renamed,
                            rewritten.getType());
                };
            }
        };
    }

    private static int[] countRenames(DexFile dex, RenameTable table) {
        int classes = 0;
        int members = 0;
        for (ClassDef classDef : dex.getClasses()) {
            if (table.types().containsKey(classDef.getType())) {
                classes++;
            }
            for (Field field : classDef.getFields()) {
                if (!table.fieldName(field.getDefiningClass(), field.getName(),
                        field.getType()).equals(field.getName())) {
                    members++;
                }
            }
            for (Method method : classDef.getMethods()) {
                if (!table.methodName(method.getDefiningClass(), method.getName(),
                        method.getParameterTypes(), method.getReturnType())
                        .equals(method.getName())) {
                    members++;
                }
            }
        }
        return new int[]{classes, members};
    }

    private static Opcodes opcodesFor(byte[] dex) {
        if (dex.length < 8) {
            return Opcodes.getDefault();
        }
        String magic = new String(dex, 0, 4, StandardCharsets.US_ASCII);
        if (!"dex\n".equals(magic)) {
            return Opcodes.getDefault();
        }
        String version = new String(dex, 4, 3, StandardCharsets.US_ASCII);
        try {
            return Opcodes.forDexVersion(Integer.parseInt(version));
        } catch (RuntimeException e) {
            return Opcodes.getDefault();
        }
    }

}
