package com.s2admin.module.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果
 */
@Data
public class ImportResultVO {

    private int created;
    private int skipped;
    private List<String> errors = new ArrayList<>();
}
