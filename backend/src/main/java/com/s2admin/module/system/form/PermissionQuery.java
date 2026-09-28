package com.s2admin.module.system.form;

import com.s2admin.module.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionQuery extends PageQuery {

    private String keyword;

    private Integer type;

    private Integer status;
}
