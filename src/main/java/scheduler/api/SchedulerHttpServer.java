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
        server.createContext("/time", this::handleTime);
        server.createContext("/machines", this::handleMachines);
        server.createContext("/machine-groups", this::handleMachineGroups);
        server.createContext("/shifts/context", this::handleShiftContext);
        server.createContext("/shifts/close", this::handleShiftClose);
    }

    public void start() {
        server.start();
    }

    private void handleSchedule(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        ScheduleView view = buildScheduleView();
        if (wantsHtml(exchange)) {
            sendHtml(exchange, 200, ScheduleHtmlRenderer.render(view), false);
            return;
        }
        sendJson(exchange, 200, GSON.toJson(view));
    }

    private void handleScheduleHtml(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        ScheduleView view = buildScheduleView();
        boolean download = queryParam(exchange.getRequestURI(), "download").map("true"::equalsIgnoreCase).orElse(true);
        sendHtml(exchange, 200, ScheduleHtmlRenderer.render(view), download);
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

    private void handleShiftContext(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        sendJson(exchange, 200, GSON.toJson(schedulerService.shiftContext()));
    }

    private void handleShiftClose(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            ShiftCloseRequest request = GSON.fromJson(readBody(exchange), ShiftCloseRequest.class);
            if (request == null) {
                sendJson(exchange, 400, "{\"error\":\"Request body is required\"}");
                return;
            }
            var result = schedulerService.closeShift(request);
            sendJson(exchange, 200, GSON.toJson(result));
        } catch (SchedulingException e) {
            sendJson(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleOrders(HttpExchange exchange) throws IOException {
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
        }
    }

    private void handleTime(HttpExchange exchange) throws IOException {
        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            TimeUpdateRequest request = GSON.fromJson(readBody(exchange), TimeUpdateRequest.class);
            if (request == null || request.currentTime() == null) {
                sendJson(exchange, 400, "{\"error\":\"currentTime is required\"}");
                return;
            }
            schedulerService.setSimulationTime(request.currentTime());
            sendJson(exchange, 200, GSON.toJson(new TimeUpdateRequest(schedulerService.time().now())));
        } catch (SchedulingException e) {
            sendJson(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleMachineGroups(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] segments = path.split("/");
        if (segments.length < 3 || segments[2].isBlank()) {
            sendJson(exchange, 404, "{\"error\":\"Group id required: /machine-groups/{id}\"}");
            return;
        }
        String groupId = segments[2];
        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            MachineGroupUpdateRequest request =
                    GSON.fromJson(readBody(exchange), MachineGroupUpdateRequest.class);
            if (request == null) {
                sendJson(exchange, 400, "{\"error\":\"Request body is required\"}");
                return;
            }
            schedulerService.updateMachineGroup(groupId, request);
            sendJson(exchange, 200, "{\"groupId\":\"" + escapeJson(groupId) + "\",\"updated\":true}");
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 404, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleMachines(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        // /machines/{machineId}
        String[] segments = path.split("/");
        if (segments.length < 3 || segments[2].isBlank()) {
            sendJson(exchange, 404, "{\"error\":\"Machine id required: /machines/{id}\"}");
            return;
        }
        String machineId = segments[2];
        if (!"PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        try {
            MachineStatusUpdateRequest request =
                    GSON.fromJson(readBody(exchange), MachineStatusUpdateRequest.class);
            if (request == null || request.status() == null) {
                sendJson(exchange, 400, "{\"error\":\"status is required\"}");
                return;
            }
            schedulerService.setMachineStatus(machineId, request.status());
            sendJson(exchange, 200, "{\"machineId\":\"" + machineId + "\",\"status\":\"" + request.status() + "\"}");
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 404, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendHtml(HttpExchange exchange, int status, String body, boolean asDownload)
            throws IOException {
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
