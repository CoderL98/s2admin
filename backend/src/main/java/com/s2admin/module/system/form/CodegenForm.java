package com.s2admin.module.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CodegenForm {

    @NotBlank
    private String module;

    @NotBlank
    private String entity;

    @NotBlank
    private String tableName;

    @NotBlank
    private String permission;

    @NotBlank
    private String path;

    private String remark;

    @NotEmpty
    private List<Field> fields;

    @Data
    public static class Field {
        @NotBlank
        private String name;
        @NotBlank
        private String javaType;
        @NotBlank
        private String label;
        private boolean query;
        private boolean required;
    }
}
