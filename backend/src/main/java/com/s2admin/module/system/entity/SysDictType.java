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
 * 字典类型
 */
@Getter
@Setter
@Entity
@Table(name = "sys_dict_type")
@SQLDelete(sql = "UPDATE sys_dict_type SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysDictType extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** 状态:0 正常 1 禁用 */
    @Column
    private Integer status = 0;
}
