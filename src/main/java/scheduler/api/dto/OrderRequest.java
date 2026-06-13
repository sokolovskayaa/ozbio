package scheduler.api.dto;

import java.util.List;

/**
 * Тело POST /orders.
 * {@code orderId} — опционально; если не указан, присваивается {@code З-ГГГГ-NNNN}.
 * {@code createdAt} — текущее время планировщика.
 * Задачи детали — из справочника {@code partDefinitions} при старте.
 */
public record OrderRequest(String orderId, List<OrderPartRequest> parts) {}
