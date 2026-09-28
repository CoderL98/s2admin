use std::io::{Cursor, Write};

use serde::Deserialize;
use zip::write::SimpleFileOptions;
use zip::ZipWriter;

use crate::error::AppError;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CodegenForm {
    pub module: Option<String>,
    pub entity: Option<String>,
    pub table_name: Option<String>,
    pub permission: Option<String>,
    pub path: Option<String>,
    pub remark: Option<String>,
    pub fields: Option<Vec<Field>>,
}

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Field {
    pub name: Option<String>,
    pub java_type: Option<String>,
    pub label: Option<String>,
    #[serde(default)]
    pub query: bool,
    #[serde(default)]
    pub required: bool,
}

pub fn generate(form: &CodegenForm) -> Result<Vec<u8>, AppError> {
    let entity_raw = form.entity.clone().unwrap_or_default();
    if entity_raw.trim().is_empty() || form.module.as_deref().unwrap_or("").trim().is_empty()
        || form.table_name.as_deref().unwrap_or("").trim().is_empty()
        || form.permission.as_deref().unwrap_or("").trim().is_empty()
        || form.path.as_deref().unwrap_or("").trim().is_empty()
        || form.fields.as_ref().map(|f| f.is_empty()).unwrap_or(true)
    {
        return Err(AppError::bad("参数校验失败"));
    }
    let entity = capitalize(entity_raw.trim());
    if !regex_ok(&entity, r"^[A-Z][A-Za-z0-9]*$") {
        return Err(AppError::bad("实体名需为 PascalCase 英文"));
    }
    let module = form.module.as_deref().unwrap_or("").trim().to_lowercase();
    if !regex_ok(&module, r"^[a-z][a-z0-9]*$") {
        return Err(AppError::bad("模块名只能是小写字母开头的英文"));
    }
    let table = form.table_name.as_deref().unwrap_or("").trim().to_string();
    if !regex_ok(&table, r"^[a-zA-Z][a-zA-Z0-9_]*$") {
        return Err(AppError::bad("表名只能包含字母、数字和下划线"));
    }
    let mut fields = form.fields.clone().unwrap_or_default();
    for field in &mut fields {
        let name = field.name.clone().unwrap_or_default().trim().to_string();
        if !regex_ok(&name, r"^[A-Za-z][A-Za-z0-9]*$") || JAVA_KEYWORDS.contains(&name.as_str()) {
            return Err(AppError::bad(format!("字段名不合法: {name}")));
        }
        field.name = Some(name);
        if let Some(label) = &mut field.label {
            *label = label.replace('\\', "").replace('\'', "").replace('\n', " ").replace('\r', "");
        }
        if field.label.as_deref().unwrap_or("").is_empty() || field.java_type.as_deref().unwrap_or("").is_empty() {
            return Err(AppError::bad("参数校验失败"));
        }
    }
    let var = uncapitalize(&entity);
    let mut cursor = Cursor::new(Vec::new());
    {
        let mut zip = ZipWriter::new(&mut cursor);
        let opts = SimpleFileOptions::default();
        put(&mut zip, opts, &format!("backend/{entity}.java"), &entity_java(&module, &table, &entity, &fields))?;
        put(&mut zip, opts, &format!("backend/{entity}Form.java"), &form_java(&module, &entity, &fields))?;
        put(&mut zip, opts, &format!("backend/{entity}Controller.java"), &controller_java(form, &module, &entity, &var))?;
        put(&mut zip, opts, &format!("backend/{entity}Service.java"), &service_java(&module, &entity, &var))?;
        put(&mut zip, opts, "frontend/+page.svelte", &svelte_page(&entity, &fields))?;
        zip.finish().map_err(|_| AppError::bad("生成代码失败"))?;
    }
    Ok(cursor.into_inner())
}

fn put(zip: &mut ZipWriter<&mut Cursor<Vec<u8>>>, opts: SimpleFileOptions, name: &str, content: &str) -> Result<(), AppError> {
    zip.start_file(name, opts).map_err(|_| AppError::bad("生成代码失败"))?;
    zip.write_all(content.as_bytes()).map_err(|_| AppError::bad("生成代码失败"))?;
    Ok(())
}

fn entity_java(module: &str, table: &str, entity: &str, fields: &[Field]) -> String {
    let mut body = String::new();
    for field in fields {
        body.push_str(&format!(
            "    @Column\n    private {} {};\n\n",
            java_type(field.java_type.as_deref()),
            uncapitalize(field.name.as_deref().unwrap_or(""))
        ));
    }
    format!(
        "package com.s2admin.module.{module}.entity;\n\nimport com.s2admin.module.common.BaseEntity;\nimport jakarta.persistence.*;\nimport lombok.Getter;\nimport lombok.Setter;\nimport org.hibernate.annotations.SQLDelete;\nimport org.hibernate.annotations.SQLRestriction;\n\n@Getter\n@Setter\n@Entity\n@Table(name = \"{table}\")\n@SQLDelete(sql = \"UPDATE {table} SET deleted = 1 WHERE id = ?\")\n@SQLRestriction(\"deleted = 0\")\npublic class {entity} extends BaseEntity {{\n{body}}}\n"
    )
}

fn form_java(module: &str, entity: &str, fields: &[Field]) -> String {
    let mut body = String::new();
    for field in fields {
        body.push_str(&format!(
            "    private {} {};\n",
            java_type(field.java_type.as_deref()),
            uncapitalize(field.name.as_deref().unwrap_or(""))
        ));
    }
    format!("package com.s2admin.module.{module}.form;\n\nimport lombok.Data;\n\n@Data\npublic class {entity}Form {{\n{body}}}\n")
}

fn controller_java(form: &CodegenForm, module: &str, entity: &str, var: &str) -> String {
    let path = form.path.as_deref().unwrap_or("");
    let path = if path.starts_with('/') { path.to_string() } else { format!("/{path}") };
    let permission = form.permission.as_deref().unwrap_or("");
    format!(
        "package com.s2admin.module.{module}.controller;\n\nimport com.s2admin.module.common.Result;\nimport com.s2admin.module.{module}.form.{entity}Form;\nimport com.s2admin.module.{module}.service.{entity}Service;\nimport jakarta.validation.Valid;\nimport lombok.RequiredArgsConstructor;\nimport org.springframework.security.access.prepost.PreAuthorize;\nimport org.springframework.web.bind.annotation.*;\n\n@RestController\n@RequestMapping(\"/api{path}\")\n@RequiredArgsConstructor\npublic class {entity}Controller {{\n\n    private final {entity}Service {var}Service;\n\n    @PostMapping\n    @PreAuthorize(\"@ss.hasPermission('{permission}:add')\")\n    public Result<?> create(@RequestBody @Valid {entity}Form form) {{\n        return Result.success({var}Service.create(form));\n    }}\n}}\n"
    )
}

fn service_java(module: &str, entity: &str, var: &str) -> String {
    format!(
        "package com.s2admin.module.{module}.service;\n\nimport com.s2admin.module.{module}.entity.{entity};\nimport com.s2admin.module.{module}.form.{entity}Form;\nimport org.springframework.stereotype.Service;\n\n@Service\npublic class {entity}Service {{\n    public {entity} create({entity}Form form) {{\n        {entity} {var} = new {entity}();\n        return {var};\n    }}\n}}\n"
    )
}

fn svelte_page(entity: &str, fields: &[Field]) -> String {
    let mut cols = String::new();
    for field in fields {
        let label = field.label.clone().unwrap_or_default();
        let name = uncapitalize(field.name.as_deref().unwrap_or(""));
        cols.push_str(&format!("        {{ header: '{label}', accessor: (r) => r.{name} }},\n"));
    }
    format!(
        "<script lang=\"ts\">\n\timport CrudTable, {{ type CrudColumn }} from '$lib/components/crud-table.svelte';\n\tconst columns: CrudColumn<Record<string, unknown>>[] = [\n{cols}\t];\n\tlet rows: Record<string, unknown>[] = $state([]);\n</script>\n\n<div class=\"flex flex-col gap-4 px-4 lg:px-6\">\n\t<h2 class=\"text-lg font-semibold\">{entity}</h2>\n\t<CrudTable data={{rows}} {{columns}} searchValue=\"\" onSearchChange={{() => {{}}}} hideActions />\n</div>\n"
    )
}

fn java_type(value: Option<&str>) -> &'static str {
    match value.unwrap_or("String") {
        "Long" | "Integer" | "Boolean" | "String" => match value.unwrap_or("String") {
            "Long" => "Long",
            "Integer" => "Integer",
            "Boolean" => "Boolean",
            _ => "String",
        },
        "int" => "Integer",
        "long" => "Long",
        _ => "String",
    }
}

fn capitalize(value: &str) -> String {
    let mut chars = value.chars();
    match chars.next() {
        None => String::new(),
        Some(c) => c.to_uppercase().collect::<String>() + chars.as_str(),
    }
}

fn uncapitalize(value: &str) -> String {
    let mut chars = value.chars();
    match chars.next() {
        None => String::new(),
        Some(c) => c.to_lowercase().collect::<String>() + chars.as_str(),
    }
}

fn regex_ok(value: &str, pattern: &str) -> bool {
    regex::Regex::new(pattern).unwrap().is_match(value)
}

const JAVA_KEYWORDS: &[&str] = &[
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "var", "record", "yield", "true", "false", "null",
];
