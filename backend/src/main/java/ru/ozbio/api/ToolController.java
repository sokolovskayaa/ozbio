package ru.ozbio.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.ozbio.api.dto.CreateToolRequest;
import ru.ozbio.api.dto.ToolResponse;
import ru.ozbio.service.ToolService;

@Order(3)
@RestController
@RequestMapping(path = "/tools", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tools", description = "Инструменты")
public class ToolController {

    private final ToolService toolService;

    public ToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать инструмент со спецификацией деталей")
    ToolResponse createTool(@Valid @RequestBody CreateToolRequest request) {
        return toolService.create(request);
    }

    @GetMapping
    @Operation(summary = "Список инструментов")
    List<ToolResponse> listTools() {
        return toolService.list();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить инструмент и его спецификацию")
    void deleteTool(@PathVariable long id) {
        toolService.delete(id);
    }
}
