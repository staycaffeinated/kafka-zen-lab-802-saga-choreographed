/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.domain.common;

/**
 * A trait for objects that have resourceId's
 */
public interface ResourceIdAware<T> {
    T getResourceId();
}
