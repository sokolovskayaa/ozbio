package scheduler.api.view;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Service
public class SchedulePageRenderer {

    private final TemplateEngine templateEngine;

    public SchedulePageRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(ScheduleView view) {
        return process(templateEngine, view);
    }

    /** Для тестов без Spring-контекста. */
    public static String renderStandalone(ScheduleView view) {
        return process(standaloneEngine(), view);
    }

    private static String process(TemplateEngine engine, ScheduleView view) {
        SchedulePageModel page = ScheduleHtmlRenderer.assemblePage(view);
        Context context = new Context();
        context.setVariable("page", page);
        return engine.process("schedule", context);
    }

    private static SpringTemplateEngine standaloneEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
