package ru.ozbio.service.model;

import java.time.Duration;
import java.util.List;

public record CreateToolCommand(String name, Duration assembleDuration, List<Detail> details) {

    public record Detail(long detailId, int count) {}
}
