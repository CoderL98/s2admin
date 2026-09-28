package com.s2admin.module.system.service;

import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.system.form.CodegenForm;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 按表单字段生成 entity/service/controller 与前端页面骨架,打包 zip。
 */
@Service
public class CodegenService {

    private static final java.util.Set<String> JAVA_KEYWORDS = java.util.Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "var", "record", "yield", "true", "false", "null");

    public byte[] generate(CodegenForm form) {
        String entity = capitalize(form.getEntity());
        if (!entity.matches("^[A-Z][A-Za-z0-9]*$")) {
            throw new BusinessException("实体名需为 PascalCase 英文");
        }
        String module = form.getModule() == null ? "" : form.getModule().trim().toLowerCase(Locale.ROOT);
        if (!module.matches("^[a-z][a-z0-9]*$")) {
            throw new BusinessException("模块名只能是小写字母开头的英文");
        }
        String table = form.getTableName() == null ? "" : form.getTableName().trim();
        if (!table.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            throw new BusinessException("表名只能包含字母、数字和下划线");
        }
        form.setTableName(table);
        if (form.getFields() != null) {
            for (CodegenForm.Field field : form.getFields()) {
                String name = field.getName() == null ? "" : field.getName().trim();
                if (!name.matches("^[A-Za-z][A-Za-z0-9]*$") || JAVA_KEYWORDS.contains(name)) {
                    throw new BusinessException("字段名不合法: " + name);
                }
                field.setName(name);
                if (field.getLabel() != null) {
                    field.setLabel(field.getLabel().replace("\\", "").replace("'", "").replace("\n", " ").replace("\r", ""));
                }
            }
        }
        String var = uncapitalize(entity);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bos)) {
            put(zip, "backend/" + entity + ".java", entityJava(form, entity, module));
            put(zip, "backend/" + entity + "Form.java", formJava(form, entity, module));
            put(zip, "backend/" + entity + "Controller.java", controllerJava(form, entity, var, module));
            put(zip, "backend/" + entity + "Service.java", serviceJava(form, entity, var, module));
            put(zip, "frontend/+page.svelte", sveltePage(form, entity, var));
            zip.finish();
            return bos.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("生成代码失败");
        }
    }

    private void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String entityJava(CodegenForm form, String entity, String module) {
        StringBuilder fields = new StringBuilder();
        for (CodegenForm.Field f : form.getFields()) {
            fields.append("    @Column\n    private ").append(javaType(f.getJavaType()))
                    .append(' ').append(uncapitalize(f.getName())).append(";\n\n");
        }
        return """
                package com.s2admin.module.%s.entity;

                import com.s2admin.module.common.BaseEntity;
                import jakarta.persistence.*;
                import lombok.Getter;
                import lombok.Setter;
                import org.hibernate.annotations.SQLDelete;
                import org.hibernate.annotations.SQLRestriction;

                @Getter
                @Setter
                @Entity
                @Table(name = "%s")
                @SQLDelete(sql = "UPDATE %s SET deleted = 1 WHERE id = ?")
                @SQLRestriction("deleted = 0")
                public class %s extends BaseEntity {
                %s}
                """.formatted(module, form.getTableName(), form.getTableName(), entity, fields);
    }

    private String formJava(CodegenForm form, String entity, String module) {
        StringBuilder fields = new StringBuilder();
        for (CodegenForm.Field f : form.getFields()) {
            fields.append("    private ").append(javaType(f.getJavaType()))
                    .append(' ').append(uncapitalize(f.getName())).append(";\n");
        }
        return """
                package com.s2admin.module.%s.form;

                import lombok.Data;

                @Data
                public class %sForm {
                %s}
                """.formatted(module, entity, fields);
    }

    private String controllerJava(CodegenForm form, String entity, String var, String module) {
        return """
                package com.s2admin.module.%s.controller;

                import com.s2admin.module.common.Result;
                import com.s2admin.module.%s.form.%sForm;
                import com.s2admin.module.%s.service.%sService;
                import jakarta.validation.Valid;
                import lombok.RequiredArgsConstructor;
                import org.springframework.security.access.prepost.PreAuthorize;
                import org.springframework.web.bind.annotation.*;

                @RestController
                @RequestMapping("/api%s")
                @RequiredArgsConstructor
                public class %sController {

                    private final %sService %sService;

                    @PostMapping
                    @PreAuthorize("@ss.hasPermission('%s:add')")
                    public Result<?> create(@RequestBody @Valid %sForm form) {
                        return Result.success(%sService.create(form));
                    }
                }
                """.formatted(module, module, entity, module, entity, form.getPath().startsWith("/") ? form.getPath() : "/" + form.getPath(),
                entity, entity, var, form.getPermission(), entity, var);
    }

    private String serviceJava(CodegenForm form, String entity, String var, String module) {
        return """
                package com.s2admin.module.%s.service;

                import com.s2admin.module.%s.entity.%s;
                import com.s2admin.module.%s.form.%sForm;
                import org.springframework.stereotype.Service;

                @Service
                public class %sService {
                    public %s create(%sForm form) {
                        %s entity = new %s();
                        return entity;
                    }
                }
                """.formatted(module, module, entity, module, entity, entity, entity, entity, var, entity);
    }

    private String sveltePage(CodegenForm form, String entity, String var) {
        StringBuilder cols = new StringBuilder();
        for (CodegenForm.Field f : form.getFields()) {
            cols.append("        { header: '").append(f.getLabel()).append("', accessor: (r) => r.")
                    .append(uncapitalize(f.getName())).append(" },\n");
        }
        return """
                <script lang="ts">
                	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
                	const columns: CrudColumn<Record<string, unknown>>[] = [
                %s	];
                	let rows: Record<string, unknown>[] = $state([]);
                </script>

                <div class="flex flex-col gap-4 px-4 lg:px-6">
                	<h2 class="text-lg font-semibold">%s</h2>
                	<CrudTable data={rows} {columns} searchValue="" onSearchChange={() => {}} hideActions />
                </div>
                """.formatted(cols, entity);
    }

    private String javaType(String type) {
        return switch (type == null ? "String" : type) {
            case "Long", "Integer", "Boolean", "String" -> type;
            case "int" -> "Integer";
            case "long" -> "Long";
            default -> "String";
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String uncapitalize(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
