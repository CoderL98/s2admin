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
 * 字典数据
 */
@Getter
@Setter
@Entity
@Table(name = "sys_dict_data")
@SQLDelete(sql = "UPDATE sys_dict_data SET deleted = 1 WHERE id = ?")
@SQLRestriction("deleted = 0")
public class SysDictData extends BaseEntity {

    @Column(name = "dict_type_id", nullable = false)
    private Long dictTypeId;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 100)
    private String value;

    @Column
    private Integer sort = 0;

    /** 状态:0 正常 1 禁用 */
    @Column
    private Integer status = 0;
}
