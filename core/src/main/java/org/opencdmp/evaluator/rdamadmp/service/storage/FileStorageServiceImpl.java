package org.opencdmp.evaluator.rdamadmp.service.storage;

import gr.cite.tools.exception.MyApplicationException;
import gr.cite.tools.logging.LoggerService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(FileStorageServiceImpl.class));
    private final FileStorageServiceProperties properties;

    @Autowired
    public FileStorageServiceImpl(FileStorageServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    public byte[] readFile(String fileRef) {
        try (FileInputStream inputStream = new FileInputStream(properties.getTransientPath() + "/" + fileRef)) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new MyApplicationException("rda file not found in shared storage!");
        }
    }
}
