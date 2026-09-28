package com.s2admin.module.system.form;

import com.s2admin.module.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeQuery extends PageQuery {

    private String keyword;

    private Integer status;

    private Integer type;
}
