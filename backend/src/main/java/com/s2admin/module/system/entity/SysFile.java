package com.s2admin.module.system.entity;

import com.s2admin.module.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 文件元数据
 */
@Getter
@Setter
@Entity
@Table(name = "sys_file")
@SQLDelete(sql = "UPDATE sys_file SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysFile extends BaseEntity {

    @Column(name = "original_name", length = 200)
    private String originalName;

    @Column(name = "stored_name", length = 200, nullable = false)
    private String storedName;

    @Column(length = 500)
    private String url;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column
    private Long size = 0L;

    @Column(length = 50)
    private String category = "default";

    /** local / s3 */
    @Column(name = "storage_type", length = 20)
    private String storageType = "local";

    @Column(name = "tenant_id")
    private Long tenantId;
}
