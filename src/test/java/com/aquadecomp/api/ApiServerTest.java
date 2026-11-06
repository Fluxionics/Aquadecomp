package com.aquadecomp.api;

import com.aquadecomp.core.Pipeline;
import com.aquadecomp.rebuild.RebuildTestSupport;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {

    @TempDir
    Path temp;

    private ApiServer server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void levantarServidor() throws Exception {
        server = new ApiServer("127.0.0.1", 0);
        server.start();
        client = HttpClient.newHttpClient();
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void detenerServidor() {
        server.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    @Test
    void indiceYHealthRespondenConVersion() throws Exception {
        HttpResponse<String> health = get("/health");
        assertEquals(200, health.statusCode());
        assertEquals("ok", json(health).get("status").getAsString());
        assertEquals(Pipeline.VERSION, json(health).get("version").getAsString());

        HttpResponse<String> index = get("/");
        assertEquals(200, index.statusCode());
        assertTrue(json(index).get("endpoints").toString().contains("/api/decompile"));
    }

    @Test
    void decompileSinApkResponde400() throws Exception {
        HttpResponse<String> response = post("/api/decompile", "{}");

        assertEquals(400, response.statusCode());
        assertTrue(json(response).get("error").getAsString().contains("apk"),
                "mensaje: " + response.body());
    }

    @Test
    void decompileDeApkInexistenteReportaFallo() throws Exception {
        String body = "{\"apk\":\"" + temp.resolve("no-existe.apk")
                .toString().replace("\\", "\\\\") + "\"}";

        HttpResponse<String> response = post("/api/decompile", body);

        assertEquals(200, response.statusCode());
        JsonObject json = json(response);
        assertEquals(false, json.get("success").getAsBoolean());
        assertTrue(json.get("errorMessage").getAsString().contains("no existe"),
                "mensaje: " + response.body());
    }

    @Test
    void inspectDevuelveEstructuraDelApk() throws Exception {
        Path apk = RebuildTestSupport.apkWith(temp.resolve("fixture.apk"),
                "AndroidManifest.xml", "<manifest package=\"com.example\"/>",
                "classes.dex", "dex-no-real");

        HttpResponse<String> response = post("/api/inspect",
                "{\"apk\":\"" + apk.toString().replace("\\", "\\\\") + "\"}");

        assertEquals(200, response.statusCode());
        JsonObject json = json(response);
        assertTrue(json.get("success").getAsBoolean(), "respuesta: " + response.body());
        assertNotNull(json.get("structure"));
        assertTrue(json.get("structure").getAsJsonObject().has("entryCount"),
                "estructura: " + response.body());
    }

    @Test
    void rebuildDeDirectorioInexistenteReportaFallo() throws Exception {
        HttpResponse<String> response = post("/api/rebuild",
                "{\"decompileDir\":\"" + temp.resolve("no-existe")
                        .toString().replace("\\", "\\\\") + "\"}");

        assertEquals(200, response.statusCode());
        JsonObject json = json(response);
        assertEquals(false, json.get("success").getAsBoolean());
        assertNotNull(json.get("errorMessage"));
        assertTrue(json.get("errorMessage").getAsString().contains("sources/"),
                "mensaje: " + response.body());
    }

    @Test
    void cuerpoNoJsonResponde400() throws Exception {
        HttpResponse<String> response = post("/api/inspect", "esto no es json");

        assertEquals(400, response.statusCode());
        assertTrue(json(response).get("error").getAsString().contains("JSON"),
                "mensaje: " + response.body());
    }

    @Test
    void rutaDesconocidaResponde404() throws Exception {
        HttpResponse<String> response = get("/nada");

        assertEquals(404, response.statusCode());
        assertTrue(json(response).get("error").getAsString().contains("Ruta no encontrada"));
    }

    @Test
    void metodoIncorrectoResponde405() throws Exception {
        HttpResponse<String> response = get("/api/decompile");

        assertEquals(405, response.statusCode());
        assertTrue(json(response).get("error").getAsString().contains("POST"),
                "mensaje: " + response.body());
    }
}
