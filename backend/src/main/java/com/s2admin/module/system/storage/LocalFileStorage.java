package com.s2admin.module.system.storage;

import com.s2admin.module.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(prefix = "s2admin.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    @Value("${s2admin.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public String type() {
        return "local";
    }

    @Override
    public Stored store(MultipartFile file, String storedName) {
        try {
            Files.createDirectories(root());
            Path target = resolve(storedName);
            file.transferTo(target);
            return new Stored(storedName, "/api/system/file/" + storedName);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败");
        }
    }

    @Override
    public Resource load(String storedName) {
        Path target = resolve(storedName);
        if (!Files.isRegularFile(target)) {
            throw BusinessException.notFound("文件不存在");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException e) {
            throw new BusinessException("文件删除失败");
        }
    }

    private Path resolve(String storedName) {
        if (storedName == null || storedName.isBlank()
                || storedName.contains("..") || storedName.contains("/") || storedName.contains("\\")) {
            throw BusinessException.notFound("文件不存在");
        }
        Path dir = root();
        Path target = dir.resolve(storedName).normalize();
        if (!target.startsWith(dir)) {
            throw BusinessException.notFound("文件不存在");
        }
        return target;
    }

    private Path root() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }
}
