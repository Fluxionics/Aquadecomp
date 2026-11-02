package com.aquadecomp.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class AxmlParser {

    private static final int RES_STRING_POOL_TYPE = 0x0001;
    private static final int RES_XML_TYPE = 0x0003;
    private static final int RES_XML_START_NAMESPACE_TYPE = 0x0100;
    private static final int RES_XML_END_NAMESPACE_TYPE = 0x0101;
    private static final int RES_XML_START_ELEMENT_TYPE = 0x0102;
    private static final int RES_XML_END_ELEMENT_TYPE = 0x0103;

    private static final int TYPE_NULL = 0x00;
    private static final int TYPE_REFERENCE = 0x01;
    private static final int TYPE_STRING = 0x03;
    private static final int TYPE_INT_DEC = 0x10;
    private static final int TYPE_INT_HEX = 0x11;
    private static final int TYPE_INT_BOOLEAN = 0x12;

    private static final int UTF8_FLAG = 0x100;
    private static final int NO_INDEX = 0xFFFFFFFF;

    private final byte[] data;
    private String[] strings = new String[0];

    private AxmlParser(byte[] data) {
        this.data = data;
    }

    static ManifestInfo parse(byte[] data) {
        if (data == null || data.length < 8) {
            return ManifestInfo.failed("Manifest vacío o demasiado pequeño");
        }
        return new AxmlParser(data).run();
    }

    private ManifestInfo run() {
        try {
            int fileHeaderType = u16(0);
            int fileHeaderSize = u16(2);
            if (fileHeaderType != RES_XML_TYPE) {
                return ManifestInfo.failed("No es un XML binario de Android (tipo=0x"
                        + Integer.toHexString(fileHeaderType) + ")");
            }

            String packageName = null;
            String versionName = null;
            String versionCode = null;
            String appClass = null;
            boolean debuggable = false;
            boolean allowBackup = false;
            List<String> permissions = new ArrayList<>();
            List<String> activities = new ArrayList<>();
            String mainActivity = null;

            String pendingActivity = null;
            boolean sawMainAction = false;
            boolean sawLauncherCategory = false;
            boolean inIntentFilter = false;
            int activityDepth = 0;

            int offset = fileHeaderSize;
            while (offset + 8 <= data.length) {
                int type = u16(offset);
                int headerSize = u16(offset + 2);
                int size = u32(offset + 4);
                if (size < 8 || offset + size > data.length) {
                    break;
                }

                switch (type) {
                    case RES_STRING_POOL_TYPE -> parseStringPool(offset);
                    case RES_XML_START_ELEMENT_TYPE -> {
                        Element el = readStartElement(offset, headerSize);
                        if (el == null) break;

                        switch (el.name) {
                            case "manifest" -> {
                                packageName = strAttr(el, "package", packageName);
                                versionName = strAttr(el, "versionName", versionName);
                                String vc = strAttr(el, "versionCode", null);
                                if (vc != null) versionCode = vc;
                            }
                            case "uses-permission", "uses-permission-sdk-23" -> {
                                String name = strAttr(el, "name", null);
                                if (name != null && !permissions.contains(name)) {
                                    permissions.add(name);
                                }
                            }
                            case "application" -> {
                                appClass = strAttr(el, "name", appClass);
                                Boolean dbg = boolAttr(el, "debuggable");
                                if (dbg != null) debuggable = dbg;
                                Boolean ab = boolAttr(el, "allowBackup");
                                if (ab != null) allowBackup = ab;
                            }
                            case "activity", "activity-alias" -> {
                                pendingActivity = strAttr(el, "name", null);
                                sawMainAction = false;
                                sawLauncherCategory = false;
                                inIntentFilter = false;

                                String full = resolveActivityName(packageName, pendingActivity);
                                if (full != null && !activities.contains(full)) {
                                    activities.add(full);
                                }
                            }
                            case "intent-filter" -> {
                                if (activityDepth > 0) inIntentFilter = true;
                            }
                            case "action" -> {
                                if (inIntentFilter && "android.intent.action.MAIN"
                                        .equals(strAttr(el, "name", null))) {
                                    sawMainAction = true;
                                }
                            }
                            case "category" -> {
                                if (inIntentFilter && "android.intent.category.LAUNCHER"
                                        .equals(strAttr(el, "name", null))) {
                                    sawLauncherCategory = true;
                                }
                            }
                            default -> {
                            }
                        }

                        if ("activity".equals(el.name) || "activity-alias".equals(el.name)) {
                            activityDepth = 1;
                        } else if (activityDepth > 0) {
                            activityDepth++;
                        }
                    }
                    case RES_XML_END_ELEMENT_TYPE -> {
                        String name = elementNameAt(offset);
                        if (activityDepth > 0) {
                            activityDepth--;

                            if (activityDepth == 0 && sawMainAction && sawLauncherCategory) {
                                String full = resolveActivityName(packageName, pendingActivity);
                                if (full != null) mainActivity = full;
                            }
                            if (activityDepth == 0) inIntentFilter = false;
                        }
                        if ("intent-filter".equals(name)) inIntentFilter = false;
                    }
                    default -> {

                    }
                }
                offset += size;
            }

            if (packageName == null) {
                return ManifestInfo.failed("No se encontró el atributo 'package' en el manifiesto");
            }
            return new ManifestInfo(packageName, versionName, versionCode, appClass,
                    debuggable, allowBackup, permissions, activities, mainActivity,
                    true, null);
        } catch (Exception e) {
            return ManifestInfo.failed("Error parseando AXML: " + e.getMessage());
        }
    }

    private void parseStringPool(int chunkOff) {
        int stringCount = u32(chunkOff + 8);
        int flags = u32(chunkOff + 16);
        int stringsStart = u32(chunkOff + 20);
        boolean utf8 = (flags & UTF8_FLAG) != 0;

        String[] out = new String[stringCount];
        for (int i = 0; i < stringCount; i++) {
            int relOff = u32(chunkOff + 28 + i * 4);
            int p = chunkOff + stringsStart + relOff;
            if (p < 0 || p >= data.length) {
                out[i] = "";
                continue;
            }
            try {
                out[i] = utf8 ? readUtf8(p) : readUtf16(p);
            } catch (Exception e) {
                out[i] = "";
            }
        }
        strings = out;
    }

    private String readUtf8(int p) {
        int byteLen = data[p] & 0xFF;
        p++;
        if ((byteLen & 0x80) != 0) {
            byteLen = ((byteLen & 0x7F) << 8) | (data[p] & 0xFF);
            p++;
        }

        int charLen = data[p] & 0xFF;
        p++;
        if ((charLen & 0x80) != 0) {
            p++;
        }
        if (p + byteLen > data.length) return "";
        return new String(data, p, byteLen, StandardCharsets.UTF_8);
    }

    private String readUtf16(int p) {
        int charLen = (data[p] & 0xFF) | ((data[p + 1] & 0xFF) << 8);
        p += 2;
        if ((charLen & 0x8000) != 0) {
            int low = (data[p] & 0xFF) | ((data[p + 1] & 0xFF) << 8);
            p += 2;
            charLen = ((charLen & 0x7FFF) << 16) | low;
        }
        if (p + charLen * 2 > data.length) return "";
        return new String(data, p, charLen * 2, StandardCharsets.UTF_16LE);
    }

    private record Element(String name, List<Attr> attrs) {
    }

    private record Attr(String name, String value, int dataType, int data) {
    }

    private Element readStartElement(int offset, int headerSize) {
        int extOff = offset + headerSize;
        if (extOff + 16 > data.length) return null;
        int nameIdx = u32(extOff + 4);
        int attrStart = u16(extOff + 8);
        int attrSize = u16(extOff + 10);
        int attrCount = u16(extOff + 12);

        int attrsOff = extOff + attrStart;
        List<Attr> attrs = new ArrayList<>(attrCount);
        for (int i = 0; i < attrCount; i++) {
            int a = attrsOff + i * attrSize;
            if (a + 20 > data.length) break;
            int attrNameIdx = u32(a + 4);
            int rawValueIdx = u32(a + 8);
            int dataType = data[a + 15] & 0xFF;
            int valueData = u32(a + 16);

            String attrName = str(attrNameIdx);
            if (attrName == null) continue;

            String value;
            if (rawValueIdx != NO_INDEX && rawValueIdx < strings.length) {
                value = strings[rawValueIdx];
            } else {
                value = valueToString(dataType, valueData);
            }
            attrs.add(new Attr(attrName, value, dataType, valueData));
        }
        String elName = str(nameIdx);
        if (elName == null) return null;
        return new Element(elName, attrs);
    }

    private String elementNameAt(int offset) {
        int headerSize = u16(offset + 2);
        int extOff = offset + headerSize;
        if (extOff + 8 > data.length) return null;
        return str(u32(extOff + 4));
    }

    private String valueToString(int dataType, int data) {
        return switch (dataType) {
            case TYPE_STRING -> str(data);
            case TYPE_REFERENCE -> "@0x" + Integer.toHexString(data);
            case TYPE_INT_DEC -> String.valueOf(data);
            case TYPE_INT_HEX -> "0x" + Integer.toHexString(data);
            case TYPE_INT_BOOLEAN -> data != 0 ? "true" : "false";
            case TYPE_NULL -> null;
            default -> str(data);
        };
    }

    private String str(int idx) {
        if (idx == NO_INDEX || idx < 0 || idx >= strings.length) return null;
        return strings[idx];
    }

    private String strAttr(Element el, String name, String def) {
        for (Attr a : el.attrs) {
            if (a.name.equals(name) && a.value != null) return a.value;
        }
        return def;
    }

    private Boolean boolAttr(Element el, String name) {
        for (Attr a : el.attrs) {
            if (a.name.equals(name)) {
                if (a.dataType == TYPE_INT_BOOLEAN) return a.data != 0;
                if (a.value != null) return "true".equalsIgnoreCase(a.value);
                return false;
            }
        }
        return null;
    }

    static String resolveActivityName(String pkg, String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.startsWith(".")) return (pkg == null ? "" : pkg) + name;
        if (!name.contains(".") && pkg != null) return pkg + "." + name;
        return name;
    }

    private int u16(int off) {
        return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
    }

    private int u32(int off) {
        return (data[off] & 0xFF)
                | ((data[off + 1] & 0xFF) << 8)
                | ((data[off + 2] & 0xFF) << 16)
                | ((data[off + 3] & 0xFF) << 24);
    }
}
