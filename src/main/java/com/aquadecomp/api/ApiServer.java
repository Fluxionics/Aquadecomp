package com.aquadecomp.api;

import com.aquadecomp.core.ApkInspector;
import com.aquadecomp.core.Pipeline;
import com.aquadecomp.core.PipelineListener;
import com.aquadecomp.core.PipelineResult;
import com.aquadecomp.rebuild.RebuildPipeline;
import com.aquadecomp.rebuild.RebuildReport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public final class ApiServer {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8080;

    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    private final HttpServer server;

    public ApiServer(String host, int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(2));
        this.server.createContext("/", this::dispatch);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if ("/".equals(path) || "/index".equals(path)) {
                handleIndex(exchange);
            } else if ("/health".equals(path)) {
                handleHealth(exchange);
            } else if ("/api/decompile".equals(path)) {
                handleDecompile(exchange);
            } else if ("/api/inspect".equals(path)) {
                handleInspect(exchange);
            } else if ("/api/rebuild".equals(path)) {
                handleRebuild(exchange);
            } else {
                sendJson(exchange, 404, error("Ruta no encontrada: " + path));
            }
        } catch (BadRequest e) {
            sendJson(exchange, 400, error(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, error("Error interno: " + e.getMessage()));
        }
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        JsonObject index = new JsonObject();
        index.addProperty("name", "Aquadecomp");
        index.addProperty("version", Pipeline.VERSION);
        index.add("endpoints", GSON.toJsonTree(List.of(
                "GET /health",
                "GET /",
                "POST /api/inspect",
                "POST /api/decompile",
                "POST /api/rebuild")));
        sendJson(exchange, 200, index);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        JsonObject health = new JsonObject();
        health.addProperty("status", "ok");
        health.addProperty("version", Pipeline.VERSION);
        sendJson(exchange, 200, health);
    }

    private void handleDecompile(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "POST");
            return;
        }
        JsonObject body = readBody(exchange);
        Path apk = requiredPath(body, "apk", "falta el campo \"apk\"");
        Path output = optionalPath(body, "output");
        if (output == null) {
            String name = apk.getFileName() != null
                    ? apk.getFileName().toString() : "apk";
            int dot = name.lastIndexOf('.');
            output = Path.of("aquadecomp-out", dot > 0 ? name.substring(0, dot) : name);
        }
        Path mapping = optionalPath(body, "mapping");
        boolean deobfuscate = optionalBoolean(body, "deobfuscate", true);
        boolean includeResources = optionalBoolean(body, "includeResources", true);
        int threads = optionalInt(body, "threads", 0);

        Pipeline pipeline = new Pipeline();
        Pipeline.Options opts = new Pipeline.Options(apk, output, deobfuscate,
                includeResources, threads, mapping);
        PipelineResult result = pipeline.run(opts, PipelineListener.SILENT);
        sendJson(exchange, 200, GSON.toJsonTree(result));
    }

    private void handleInspect(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "POST");
            return;
        }
        JsonObject body = readBody(exchange);
        Path apk = requiredPath(body, "apk", "falta el campo \"apk\"");

        ApkInspector inspector = new ApkInspector();
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.add("structure", GSON.toJsonTree(inspector.inspect(apk)));
        response.add("manifest", GSON.toJsonTree(inspector.readManifest(apk)));
        sendJson(exchange, 200, response);
    }

    private void handleRebuild(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "POST");
            return;
        }
        JsonObject body = readBody(exchange);
        Path dir = requiredPath(body, "decompileDir",
                "falta el campo \"decompileDir\"");

        RebuildPipeline.Options opts = new RebuildPipeline.Options(
                dir,
                optionalPath(body, "original"),
                optionalPath(body, "output"),
                optionalPath(body, "androidJar"),
                optionalStringList(body, "files"),
                optionalBoolean(body, "wholeApp", false),
                optionalInt(body, "minApi", 21),
                optionalPath(body, "mapping"));
        RebuildReport report = new RebuildPipeline().run(opts, PipelineListener.SILENT);
        sendJson(exchange, 200, GSON.toJsonTree(report));
    }

    private static JsonObject readBody(HttpExchange exchange) {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new BadRequest("Se espera una petición POST");
        }
        String raw;
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new BadRequest("Cuerpo demasiado grande (máximo "
                        + (MAX_BODY_BYTES / 1024 / 1024) + " MB)");
            }
            raw = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BadRequest("No se pudo leer el cuerpo: " + e.getMessage());
        }
        if (raw.isBlank()) {
            throw new BadRequest("El cuerpo debe ser JSON");
        }
        try {
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new BadRequest("El cuerpo no es un JSON válido");
        }
    }

    private static Path requiredPath(JsonObject body, String field, String message) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            throw new BadRequest(message);
        }
        String value = body.get(field).getAsString();
        if (value == null || value.isBlank()) {
            throw new BadRequest(message);
        }
        return Path.of(value);
    }

    private static Path optionalPath(JsonObject body, String field) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            return null;
        }
        String value = body.get(field).getAsString();
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static boolean optionalBoolean(JsonObject body, String field, boolean fallback) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            return fallback;
        }
        try {
            return body.get(field).getAsBoolean();
        } catch (RuntimeException e) {
            throw new BadRequest("El campo \"" + field + "\" debe ser booleano");
        }
    }

    private static int optionalInt(JsonObject body, String field, int fallback) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            return fallback;
        }
        try {
            return body.get(field).getAsInt();
        } catch (RuntimeException e) {
            throw new BadRequest("El campo \"" + field + "\" debe ser un número entero");
        }
    }

    private static List<String> optionalStringList(JsonObject body, String field) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            return List.of();
        }
        try {
            List<String> values = new ArrayList<>();
            for (var element : body.getAsJsonArray(field)) {
                values.add(element.getAsString());
            }
            return List.copyOf(values);
        } catch (RuntimeException e) {
            throw new BadRequest("El campo \"" + field + "\" debe ser una lista de textos");
        }
    }

    private static void methodNotAllowed(HttpExchange exchange, String method) throws IOException {
        sendJson(exchange, 405, error("Método no permitido: usa " + method
                + " en " + exchange.getRequestURI().getPath()));
    }

    private static JsonObject error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("success", false);
        json.addProperty("error", message);
        return json;
    }

    private static void sendJson(HttpExchange exchange, int status, JsonElement body)
            throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static final class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }
}
