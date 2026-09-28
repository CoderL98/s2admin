package com.s2admin.module.system.form;

import com.s2admin.module.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfigQuery extends PageQuery {

    private String keyword;

    private String groupCode;

    private String configType;
}
