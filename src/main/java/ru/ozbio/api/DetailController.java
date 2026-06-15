package ru.ozbio.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.ozbio.api.dto.CreateDetailRequest;
import ru.ozbio.api.dto.DetailResponse;
import ru.ozbio.service.DetailService;

@RestController
@RequestMapping(path = "/details", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Details", description = "Детали")
public class DetailController {

    private final DetailService detailService;

    public DetailController(DetailService detailService) {
        this.detailService = detailService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать деталь с маршрутом производства")
    DetailResponse createDetail(@Valid @RequestBody CreateDetailRequest request) {
        return detailService.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить деталь и её маршрут производства")
    void deleteDetail(@PathVariable long id) {
        detailService.delete(id);
    }
}
