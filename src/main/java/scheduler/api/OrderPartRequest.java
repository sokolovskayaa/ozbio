package scheduler.api;

/** Позиция заказа: тип детали и количество. */
public record OrderPartRequest(String partId, Integer quantity) {

    public int resolvedQuantity() {
        if (quantity == null || quantity < 1) {
            return 1;
        }
        return quantity;
    }
}
