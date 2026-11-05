package com.aquadecomp.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DecodedManifestReader {

    private static final String ANDROID_NS = "android:name";

    private DecodedManifestReader() {
    }

    public static ManifestInfo read(Path xmlFile) {
        if (!Files.isRegularFile(xmlFile)) {
            return ManifestInfo.failed("No hay manifiesto decodificado: " + xmlFile.getFileName());
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(xmlFile.toFile());
            Element manifest = doc.getDocumentElement();
            if (manifest == null || !"manifest".equals(manifest.getTagName())) {
                return ManifestInfo.failed("El manifiesto decodificado no tiene raíz <manifest>");
            }

            String packageName = emptyToNull(manifest.getAttribute("package"));
            if (packageName == null) {
                return ManifestInfo.failed("El manifiesto decodificado no tiene atributo package");
            }

            String versionName = emptyToNull(manifest.getAttribute("android:versionName"));
            String versionCode = emptyToNull(manifest.getAttribute("android:versionCode"));

            Element application = firstElement(doc, "application");
            String applicationClass = application == null
                    ? null : emptyToNull(application.getAttribute(ANDROID_NS));
            boolean debuggable = application != null
                    && "true".equals(application.getAttribute("android:debuggable"));
            boolean allowBackup = application != null
                    && "true".equals(application.getAttribute("android:allowBackup"));

            List<String> permissions = elementNames(doc, "uses-permission");
            List<String> activities = elementNames(doc, "activity");
            String mainActivity = findLauncherActivity(doc);

            return new ManifestInfo(packageName, versionName, versionCode, applicationClass,
                    debuggable, allowBackup, permissions, activities, mainActivity, true, null);
        } catch (Exception e) {
            return ManifestInfo.failed("Error leyendo manifiesto decodificado: " + e.getMessage());
        }
    }

    private static String findLauncherActivity(Document doc) {
        NodeList activities = doc.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (hasLauncherCategory(activity)) {
                String name = emptyToNull(activity.getAttribute(ANDROID_NS));
                if (name != null) {
                    return name;
                }
            }
        }
        return null;
    }

    private static boolean hasLauncherCategory(Element element) {
        NodeList categories = element.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Node name = categories.item(i).getAttributes().getNamedItem(ANDROID_NS);
            if (name != null && "android.intent.category.LAUNCHER".equals(name.getNodeValue())) {
                return true;
            }
        }
        return false;
    }

    private static Element firstElement(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static List<String> elementNames(Document doc, String tag) {
        List<String> names = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            String name = emptyToNull(((Element) nodes.item(i)).getAttribute(ANDROID_NS));
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
