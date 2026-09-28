package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.form.FileQuery;
import com.s2admin.module.system.service.FileService;
import com.s2admin.module.system.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/system/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('system:file:view')")
    public Result<PageResult<FileVO>> page(FileQuery query) {
        return Result.success(fileService.page(query));
    }

    @PostMapping("/upload")
    @PreAuthorize("@ss.hasPermission('system:file:view')")
    @Log(module = "文件", operation = "上传")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "category", required = false) String category) {
        return Result.success(fileService.upload(file, category));
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> download(@PathVariable String filename) {
        Resource resource = fileService.load(filename);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(fileService.contentType(filename));
        } catch (Exception ignored) {
        }
        ContentDisposition disposition = (FileService.previewable(mediaType.toString())
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    @DeleteMapping("/id/{id}")
    @PreAuthorize("@ss.hasPermission('system:file:delete')")
    @Log(module = "文件", operation = "删除")
    public Result<Void> deleteById(@PathVariable Long id) {
        fileService.deleteById(id);
        return Result.success();
    }

    @DeleteMapping("/{filename:.+}")
    @PreAuthorize("@ss.hasPermission('system:file:delete')")
    @Log(module = "文件", operation = "删除")
    public Result<Void> delete(@PathVariable String filename) {
        fileService.delete(filename);
        return Result.success();
    }
}
