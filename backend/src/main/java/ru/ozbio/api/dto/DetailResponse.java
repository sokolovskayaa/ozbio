package ru.ozbio.api.dto;

import java.util.List;

public record DetailResponse(long id, String name, List<OperationResponse> operations) {}
