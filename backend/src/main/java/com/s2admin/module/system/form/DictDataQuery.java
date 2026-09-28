package com.s2admin.module.system.form;

import com.s2admin.module.common.PageQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictDataQuery extends PageQuery {

    @NotNull(message = "字典类型不能为空")
    private Long dictTypeId;

    private String keyword;

    private Integer status;
}
