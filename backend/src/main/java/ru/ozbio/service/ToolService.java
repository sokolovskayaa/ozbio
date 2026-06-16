package ru.ozbio.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ozbio.api.dto.CreateToolRequest;
import ru.ozbio.api.dto.ToolDetailResponse;
import ru.ozbio.api.dto.ToolResponse;
import ru.ozbio.persistence.ToolRepository;
import ru.ozbio.service.exception.InvalidReferenceException;
import ru.ozbio.service.model.ToolDetailLine;
import ru.ozbio.service.model.ToolSummary;

@Service
public class ToolService {

    private final ToolRepository toolRepository;

    public ToolService(ToolRepository toolRepository) {
        this.toolRepository = toolRepository;
    }

    @Transactional
    public ToolResponse create(CreateToolRequest request) {
        validateDetails(request);

        ToolSummary tool = toolRepository.insert(request.toCommand());
        return toResponse(tool, toolRepository.findDetailsByToolId(tool.id()));
    }

    public List<ToolResponse> list() {
        List<ToolSummary> tools = toolRepository.findAll();
        var detailsByToolId =
                toolRepository.findDetailsByToolIds(tools.stream().map(ToolSummary::id).toList());

        return tools.stream()
                .map(
                        tool ->
                                toResponse(
                                        tool,
                                        detailsByToolId.getOrDefault(tool.id(), List.of())))
                .toList();
    }

    @Transactional
    public void delete(long id) {
        toolRepository.deleteById(id);
    }

    private void validateDetails(CreateToolRequest request) {
        Set<Long> detailIds = new HashSet<>();
        for (var detail : request.details()) {
            if (!detailIds.add(detail.detailId())) {
                throw new IllegalArgumentException("Duplicate detailId: " + detail.detailId());
            }
            if (!toolRepository.detailExists(detail.detailId())) {
                throw new InvalidReferenceException("detailId", detail.detailId());
            }
        }
    }

    private ToolResponse toResponse(ToolSummary tool, List<ToolDetailLine> details) {
        List<ToolDetailResponse> detailResponses =
                details.stream()
                        .map(detail -> new ToolDetailResponse(detail.detailId(), detail.detailName(), detail.count()))
                        .toList();
        return new ToolResponse(
                tool.id(), tool.name(), (int) tool.assembleDuration().toMinutes(), detailResponses);
    }
}
