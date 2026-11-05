package com.aquadecomp.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecodedManifestReaderTest {

    @TempDir
    Path tmp;

    private Path write(String content) throws IOException {
        Path file = tmp.resolve("AndroidManifest.xml");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void readsPackagePermissionsAndLauncherActivity() throws IOException {
        Path xml = write("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.app"
                    android:versionName="2.5.1"
                    android:versionCode="42">
                    <uses-permission android:name="android.permission.INTERNET"/>
                    <uses-permission android:name="android.permission.CAMERA"/>
                    <application android:name=".App" android:debuggable="true"
                        android:allowBackup="true">
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN"/>
                                <category android:name="android.intent.category.LAUNCHER"/>
                            </intent-filter>
                        </activity>
                        <activity android:name=".Secundaria"/>
                    </application>
                </manifest>
                """);

        ManifestInfo m = DecodedManifestReader.read(xml);

        assertTrue(m.parsedOk());
        assertEquals("com.example.app", m.packageName());
        assertEquals("2.5.1", m.versionName());
        assertEquals("42", m.versionCode());
        assertEquals(".App", m.applicationClass());
        assertTrue(m.debuggable());
        assertTrue(m.allowBackup());
        assertEquals(2, m.permissions().size());
        assertEquals(2, m.activities().size());
        assertEquals(".MainActivity", m.mainActivity());
    }

    @Test
    void findLocatesNestedManifestInBundleLayout() throws IOException {
        Path nested = tmp.resolve("resources/base/manifest/AndroidManifest.xml");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "<manifest package=\"com.example.app\"/>");

        Path found = DecodedManifestReader.find(tmp.resolve("resources"));

        assertEquals(nested, found);
        assertTrue(DecodedManifestReader.read(found).parsedOk());
    }

    @Test
    void findPrefersTheShallowestManifest() throws IOException {
        Path shallow = tmp.resolve("resources/AndroidManifest.xml");
        Path deep = tmp.resolve("resources/base/manifest/AndroidManifest.xml");
        Files.createDirectories(deep.getParent());
        Files.writeString(shallow, "<manifest package=\"com.example.shallow\"/>");
        Files.writeString(deep, "<manifest package=\"com.example.deep\"/>");

        assertEquals(shallow, DecodedManifestReader.find(tmp.resolve("resources")));
    }

    @Test
    void findReturnsTheExpectedPathWhenNothingExists() {
        Path resources = tmp.resolve("resources");

        assertEquals(resources.resolve("AndroidManifest.xml"),
                DecodedManifestReader.find(resources));
        assertFalse(Files.exists(DecodedManifestReader.find(resources)));
    }

    @Test
    void missingFileFailsCleanly() {
        ManifestInfo m = DecodedManifestReader.read(tmp.resolve("no-existe.xml"));

        assertFalse(m.parsedOk());
        assertTrue(m.parseError().contains("No hay manifiesto decodificado"));
    }

    @Test
    void nonXmlContentFailsCleanly() throws IOException {
        Path xml = write("esto no es XML");

        ManifestInfo m = DecodedManifestReader.read(xml);

        assertFalse(m.parsedOk());
        assertNotNull(m.parseError());
    }

    @Test
    void manifestWithoutPackageFailsCleanly() throws IOException {
        Path xml = write("<manifest><application/></manifest>");

        ManifestInfo m = DecodedManifestReader.read(xml);

        assertFalse(m.parsedOk());
        assertTrue(m.parseError().contains("package"));
    }

    @Test
    void launcherSearchSkipsActivitiesWithoutCategoryName() throws IOException {
        Path xml = write("""
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.app">
                    <application>
                        <activity android:name=".SinCategoria">
                            <intent-filter><category/></intent-filter>
                        </activity>
                        <activity android:name=".Principal">
                            <intent-filter>
                                <category android:name="android.intent.category.LAUNCHER"/>
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """);

        ManifestInfo m = DecodedManifestReader.read(xml);

        assertTrue(m.parsedOk());
        assertEquals(".Principal", m.mainActivity());
    }
}
