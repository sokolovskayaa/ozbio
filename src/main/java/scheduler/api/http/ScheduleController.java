package scheduler.api.http;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scheduler.api.dto.OrderRequest;
import scheduler.api.view.SchedulePageRenderer;
import scheduler.api.view.ScheduleView;
import scheduler.api.view.ScheduleViewBuilder;
import scheduler.engine.machine.MachineStateSync;
import scheduler.service.AddOrderResult;
import scheduler.service.SchedulerService;
import scheduler.store.core.ScheduleStore;

@RestController
public class ScheduleController {

    private final SchedulerService schedulerService;
    private final SchedulePageRenderer schedulePageRenderer;

    public ScheduleController(SchedulerService schedulerService, SchedulePageRenderer schedulePageRenderer) {
        this.schedulerService = schedulerService;
        this.schedulePageRenderer = schedulePageRenderer;
    }

    @GetMapping(path = "/schedule", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE})
    public ResponseEntity<?> schedule(
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept)
            throws IOException {
        ScheduleView view = buildScheduleView();
        if (wantsHtml(format, accept)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(schedulePageRenderer.render(view));
        }
        return ResponseEntity.ok(view);
    }

    @GetMapping(path = "/schedule.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> scheduleHtml(
            @RequestParam(name = "download", defaultValue = "true") boolean download) throws IOException {
        ScheduleView view = buildScheduleView();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().contentType(MediaType.TEXT_HTML);
        if (download) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"schedule.html\"");
        }
        return builder.body(schedulePageRenderer.render(view));
    }

    @PostMapping(path = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AddOrderResult addOrder(@RequestBody OrderRequest request) throws IOException {
        return schedulerService.addOrder(request);
    }

    private ScheduleView buildScheduleView() {
        ScheduleStore store = schedulerService.store();
        MachineStateSync.sync(store, schedulerService.time().now());
        return ScheduleViewBuilder.build(store, schedulerService.time());
    }

    private static boolean wantsHtml(String format, String accept) {
        if (format != null && "html".equalsIgnoreCase(format)) {
            return true;
        }
        return accept != null && accept.contains("text/html");
    }
}
