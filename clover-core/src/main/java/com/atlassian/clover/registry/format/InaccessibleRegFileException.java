package com.atlassian.clover.registry.format;

import org.openclover.runtime.api.registry.CloverRegistryException;

public class InaccessibleRegFileException extends CloverRegistryException {
    public InaccessibleRegFileException(String message) {
        super(message);
    }
}
