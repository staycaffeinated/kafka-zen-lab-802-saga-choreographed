/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.application.port.inbound;

import java.util.List;

public interface HandleInventoryCommandUseCase {
    void reserveInventory(String sagaId, String orderId, List<Item> items);

    void releaseInventory(String sagaId, String orderId);

    record Item(String productId, int quantity) {}
}
