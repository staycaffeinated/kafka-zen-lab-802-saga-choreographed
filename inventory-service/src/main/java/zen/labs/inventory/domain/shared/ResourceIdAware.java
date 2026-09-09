/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.domain.shared;

/**
 * A trait for objects that have resourceId's
 */
public interface ResourceIdAware<T> {
    T getResourceId();
}
