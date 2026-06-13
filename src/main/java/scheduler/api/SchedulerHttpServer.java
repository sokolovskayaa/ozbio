package scheduler.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import scheduler.engine.MachineStateSync;
import scheduler.store.ScheduleStore;
import scheduler.service.AddOrderResult;
import scheduler.service.SchedulerService;
import scheduler.service.SchedulingException;

public class SchedulerHttpServer {
    private static final Gson GSON = GsonConfig.create();

    private final HttpServer server;
    private final SchedulerService schedulerService;

    public SchedulerHttpServer(SchedulerService schedulerService, int port) throws IOException {
        this.schedulerService = schedulerService;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/schedule", this::handleSchedule);
        server.createContext("/schedule.html", this::handleScheduleHtml);
        server.createContext("/orders", this::handleOrders);
    }

    public void start() {
        server.start();
    }

    private void handleSchedule(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            ScheduleView view = buildScheduleView();
            if (wantsHtml(exchange)) {
                sendHtml(exchange, 200, ScheduleHtmlRenderer.render(view), false);
                return;
            }
            sendJson(exchange, 200, GSON.toJson(view));
        } catch (IOException e) {
            sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
        }
    }

    private void handleScheduleHtml(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            ScheduleView view = buildScheduleView();
            boolean download = queryParam(exchange.getRequestURI(), "download").map("true"::equalsIgnoreCase).orElse(true);
            sendHtml(exchange, 200, ScheduleHtmlRenderer.render(view), download);
        } catch (IOException e) {
            sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
        }
    }

    private ScheduleView buildScheduleView() {
        ScheduleStore store = schedulerService.store();
        MachineStateSync.sync(store, schedulerService.time().now());
        return ScheduleViewBuilder.build(store, schedulerService.time());
    }

    private static boolean wantsHtml(HttpExchange exchange) {
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if (accept != null && accept.contains("text/html")) {
            return true;
        }
        return queryParam(exchange.getRequestURI(), "format").map("html"::equalsIgnoreCase).orElse(false);
    }

    private static java.util.Optional<String> queryParam(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return java.util.Optional.empty();
        }
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            String key = eq >= 0 ? part.substring(0, eq) : part;
            if (key.equals(name)) {
                return java.util.Optional.of(eq >= 0 ? part.substring(eq + 1) : "");
            }
        }
        return java.util.Optional.empty();
    }

    private void handleOrders(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        String body = readBody(exchange);
        try {
            OrderRequest request = GSON.fromJson(body, OrderRequest.class);
            AddOrderResult result = schedulerService.addOrder(request);
            sendJson(exchange, 201, GSON.toJson(result));
        } catch (SchedulingException e) {
            sendJson(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (IOException e) {
            sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        addCorsHeaders(exchange);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendHtml(HttpExchange exchange, int status, String body, boolean asDownload)
            throws IOException {
        addCorsHeaders(exchange);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        if (asDownload) {
            exchange.getResponseHeaders()
                    .add("Content-Disposition", "attachment; filename=\"schedule.html\"");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String escapeJson(String message) {
        return message == null ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
