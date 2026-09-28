package com.s2admin.module.system.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

    String type();

    Stored store(MultipartFile file, String storedName);

    Resource load(String storedName);

    void delete(String storedName);

    record Stored(String storedName, String url) {
    }
}
