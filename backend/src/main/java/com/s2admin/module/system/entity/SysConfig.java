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
 * 系统配置
 */
@Getter
@Setter
@Entity
@Table(name = "sys_config")
@SQLDelete(sql = "UPDATE sys_config SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysConfig extends BaseEntity {

    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    @Column(name = "config_value", length = 500)
    private String configValue;

    /** 类型:string number boolean */
    @Column(name = "config_type", length = 20)
    private String configType = "string";

    @Column(name = "group_code", length = 50)
    private String groupCode = "default";
}
