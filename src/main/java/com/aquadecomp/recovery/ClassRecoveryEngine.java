package com.aquadecomp.recovery;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ClassRecoveryEngine {

    private final Map<String, ClassInfo> recoveredClasses = new HashMap<>();
    private static final Pattern CLASS_DEF = Pattern.compile(
            "class\\s+([a-zA-Z0-9$_]+)\\s*(?:extends|implements|\\{)"
    );
    private static final Pattern INTERFACE_DEF = Pattern.compile(
            "interface\\s+([a-zA-Z0-9$_]+)"
    );
    private static final Pattern ENUM_DEF = Pattern.compile(
            "enum\\s+([a-zA-Z0-9$_]+)"
    );

    public String recoverClasses(String source) {
        String result = source;

        result = recoverInnerClasses(result);
        result = recoverAnonymousClasses(result);
        result = recoverMissingClasses(result);

        return result;
    }

    private String recoverInnerClasses(String source) {
        java.util.regex.Matcher matcher = CLASS_DEF.matcher(source);

        while (matcher.find()) {
            try {
                String className = matcher.group(1);

                if (className.contains("$")) {
                    String[] parts = className.split("\\$");
                    String outerClass = parts[0];
                    String innerClass = parts[parts.length - 1];

                    ClassInfo info = new ClassInfo(className, true, outerClass);
                    recoveredClasses.put(className, info);
                }
            } catch (Exception ignored) {
            }
        }

        return source;
    }

    private String recoverAnonymousClasses(String source) {
        Pattern anonPattern = Pattern.compile(
                "new\\s+([a-zA-Z0-9$_]+)\\s*\\(\\s*\\)\\s*\\{|" +
                "new\\s+([a-zA-Z0-9.]+)\\s*\\(\\s*\\)\\s*\\{"
        );
        java.util.regex.Matcher matcher = anonPattern.matcher(source);

        while (matcher.find()) {
            try {
                String className = matcher.group(1);
                if (className == null) {
                    className = matcher.group(2);
                }

                if (className != null) {
                    ClassInfo info = new ClassInfo(className, false, null);
                    recoveredClasses.put(className, info);
                }
            } catch (Exception ignored) {
            }
        }

        return source;
    }

    private String recoverMissingClasses(String source) {
        Pattern missingPattern = Pattern.compile(
                "class\\s+([a-zA-Z0-9$_]+)\\s+\\{\\s*\\}"
        );
        java.util.regex.Matcher matcher = missingPattern.matcher(source);

        while (matcher.find()) {
            try {
                String className = matcher.group(1);
                ClassInfo info = new ClassInfo(className, false, null);

                if (shouldExpandClass(className, source)) {
                    recoveredClasses.put(className, info);
                }
            } catch (Exception ignored) {
            }
        }

        return source;
    }

    private boolean shouldExpandClass(String className, String source) {
        int references = 0;
        Pattern refPattern = Pattern.compile("\\b" + Pattern.quote(className) + "\\b");
        java.util.regex.Matcher matcher = refPattern.matcher(source);

        while (matcher.find()) {
            references++;
        }

        return references > 2;
    }

    public Map<String, ClassInfo> getRecoveredClasses() {
        return new HashMap<>(recoveredClasses);
    }

    public static final class ClassInfo {
        private final String className;
        private final boolean isInner;
        private final String outerClass;
        private final Set<String> methods = new HashSet<>();
        private final Set<String> fields = new HashSet<>();

        public ClassInfo(String className, boolean isInner, String outerClass) {
            this.className = className;
            this.isInner = isInner;
            this.outerClass = outerClass;
        }

        public String className() {
            return className;
        }

        public boolean isInner() {
            return isInner;
        }

        public String outerClass() {
            return outerClass;
        }

        public void addMethod(String method) {
            methods.add(method);
        }

        public void addField(String field) {
            fields.add(field);
        }

        public Set<String> methods() {
            return new HashSet<>(methods);
        }

        public Set<String> fields() {
            return new HashSet<>(fields);
        }
    }
}
