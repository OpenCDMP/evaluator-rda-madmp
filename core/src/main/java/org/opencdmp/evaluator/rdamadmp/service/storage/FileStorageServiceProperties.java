package org.opencdmp.evaluator.rdamadmp.service.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "file.storage")
public class FileStorageServiceProperties {
    private final String transientPath;

    @ConstructorBinding
    public FileStorageServiceProperties(String transientPath) {
        this.transientPath = transientPath;
    }

    public String getTransientPath() {
        return transientPath;
    }
}
