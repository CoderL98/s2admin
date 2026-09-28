package com.s2admin.module.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileVO {

    private Long id;
    private String url;
    private String name;
    private String storedName;
    private long size;
    private String category;
    private String contentType;
    private String storageType;
    private LocalDateTime createTime;

    public FileVO(String url, String name, long size) {
        this.url = url;
        this.name = name;
        this.size = size;
    }
}
