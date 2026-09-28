use std::io::{Cursor, Read, Write};

use zip::write::SimpleFileOptions;
use zip::{ZipArchive, ZipWriter};

use crate::error::AppError;
use crate::util::neutralize;

pub fn write_xlsx(headers: &[String], rows: &[Vec<String>]) -> Result<Vec<u8>, AppError> {
    let mut cursor = Cursor::new(Vec::new());
    let mut zip = ZipWriter::new(&mut cursor);
    let opts = SimpleFileOptions::default();
    put(&mut zip, opts, "[Content_Types].xml", &content_types())?;
    put(&mut zip, opts, "_rels/.rels", RELS)?;
    put(&mut zip, opts, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS)?;
    put(&mut zip, opts, "xl/workbook.xml", WORKBOOK)?;
    put(&mut zip, opts, "xl/worksheets/sheet1.xml", &sheet(headers, rows))?;
    zip.finish().map_err(|e| AppError::bad(format!("导出 Excel 失败: {e}")))?;
    Ok(cursor.into_inner())
}

pub fn read_xlsx(bytes: &[u8]) -> Result<Vec<Vec<String>>, AppError> {
    let mut archive = ZipArchive::new(Cursor::new(bytes)).map_err(|_| AppError::bad("解析 Excel 失败"))?;
    let mut sheet = None;
    let mut shared = None;
    for i in 0..archive.len() {
        let mut file = archive.by_index(i).map_err(|_| AppError::bad("解析 Excel 失败"))?;
        let name = file.name().to_string();
        let mut buf = String::new();
        if name == "xl/worksheets/sheet1.xml" {
            file.read_to_string(&mut buf).map_err(|_| AppError::bad("解析 Excel 失败"))?;
            sheet = Some(buf);
        } else if name == "xl/sharedStrings.xml" {
            file.read_to_string(&mut buf).map_err(|_| AppError::bad("解析 Excel 失败"))?;
            shared = Some(buf);
        }
    }
    let Some(sheet) = sheet else {
        return Ok(Vec::new());
    };
    let strings = shared.map(|xml| parse_shared_strings(&xml)).unwrap_or_default();
    Ok(parse_sheet(&sheet, &strings))
}

fn put(zip: &mut ZipWriter<&mut Cursor<Vec<u8>>>, opts: SimpleFileOptions, name: &str, content: &str) -> Result<(), AppError> {
    zip.start_file(name, opts).map_err(|e| AppError::Internal(e.to_string()))?;
    zip.write_all(content.as_bytes()).map_err(|e| AppError::Internal(e.to_string()))?;
    Ok(())
}

fn content_types() -> String {
    r#"<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package-relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"#
        .into()
}

const RELS: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"#;

const WORKBOOK_RELS: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"#;

const WORKBOOK: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="users" sheetId="1" r:id="rId1"/></sheets>
</workbook>"#;

fn sheet(headers: &[String], rows: &[Vec<String>]) -> String {
    let mut sb = String::from(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>"#,
    );
    write_row(&mut sb, 1, headers);
    for (i, row) in rows.iter().enumerate() {
        write_row(&mut sb, (i + 2) as i32, row);
    }
    sb.push_str("</sheetData></worksheet>");
    sb
}

fn write_row(sb: &mut String, row_num: i32, cols: &[String]) {
    sb.push_str(&format!(r#"<row r="{row_num}">"#));
    for (i, col) in cols.iter().enumerate() {
        let reference = format!("{}{row_num}", col_name(i));
        let value = xml_escape(&neutralize(col));
        sb.push_str(&format!(
            r#"<c r="{reference}" t="inlineStr"><is><t xml:space="preserve">{value}</t></is></c>"#
        ));
    }
    sb.push_str("</row>");
}

fn col_name(index: usize) -> String {
    let mut n = index as i32;
    let mut sb = String::new();
    loop {
        sb.insert(0, char::from(b'A' + (n % 26) as u8));
        n = n / 26 - 1;
        if n < 0 {
            break;
        }
    }
    sb
}

fn xml_escape(value: &str) -> String {
    value.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;")
}

fn xml_unescape(value: &str) -> String {
    value.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}

fn parse_shared_strings(xml: &str) -> Vec<String> {
    let mut strings = Vec::new();
    let mut idx = 0;
    while let Some(start) = xml[idx..].find("<si") {
        let start = idx + start;
        let Some(end_rel) = xml[start..].find("</si>") else { break };
        let end = start + end_rel;
        strings.push(text_nodes(&xml[start..end]));
        idx = end + 5;
    }
    strings
}

fn parse_sheet(xml: &str, shared: &[String]) -> Vec<Vec<String>> {
    let mut rows = Vec::new();
    let mut row_idx = 0;
    while let Some(rs_rel) = xml[row_idx..].find("<row") {
        let rs = row_idx + rs_rel;
        let Some(re_rel) = xml[rs..].find("</row>") else { break };
        let re = rs + re_rel;
        let row_xml = &xml[rs..re];
        let mut cells = Vec::new();
        let mut values = Vec::new();
        let mut width = 0usize;
        let mut cidx = 0;
        loop {
            let cs = row_xml[cidx..].find("<c ").map(|p| cidx + p);
            let cs2 = row_xml[cidx..].find("<c>").map(|p| cidx + p);
            let start = match (cs, cs2) {
                (Some(a), Some(b)) => a.min(b),
                (Some(a), None) => a,
                (None, Some(b)) => b,
                (None, None) => break,
            };
            let Some(tag_end_rel) = row_xml[start..].find('>') else { break };
            let tag_end = start + tag_end_rel;
            let open = &row_xml[start..=tag_end];
            let (body, next) = if open.ends_with("/>") {
                ("", tag_end + 1)
            } else {
                let Some(close_rel) = row_xml[tag_end..].find("</c>") else { break };
                let close = tag_end + close_rel;
                (&row_xml[tag_end + 1..close], close + 4)
            };
            let col = column_index(open);
            cells.push((col, values.len()));
            values.push(cell_text(open, body, shared));
            width = width.max(col + 1);
            cidx = next;
        }
        if !values.is_empty() {
            let mut cols = vec![String::new(); width];
            for (col, value_idx) in cells {
                if col < cols.len() {
                    cols[col] = values[value_idx].clone();
                }
            }
            rows.push(cols);
        }
        row_idx = re + 6;
    }
    rows
}

fn cell_text(open: &str, body: &str, shared: &[String]) -> String {
    let kind = attr(open, "t");
    if kind == "inlineStr" {
        return text_nodes(body);
    }
    let raw = value_node(body);
    if kind == "s" {
        return raw
            .trim()
            .parse::<usize>()
            .ok()
            .and_then(|i| shared.get(i).cloned())
            .unwrap_or_default();
    }
    raw
}

fn text_nodes(xml: &str) -> String {
    let mut text = String::new();
    let mut t = 0;
    while let Some(ts_rel) = xml[t..].find("<t") {
        let ts = t + ts_rel;
        let Some(gt_rel) = xml[ts..].find('>') else { break };
        let gt = ts + gt_rel;
        let Some(te_rel) = xml[gt..].find("</t>") else { break };
        let te = gt + te_rel;
        text.push_str(&xml_unescape(&xml[gt + 1..te]));
        t = te + 4;
    }
    text
}

fn value_node(xml: &str) -> String {
    let Some(vs) = xml.find("<v>") else {
        return text_nodes(xml);
    };
    let Some(ve) = xml[vs..].find("</v>") else {
        return String::new();
    };
    xml_unescape(&xml[vs + 3..vs + ve])
}

fn attr(open: &str, name: &str) -> String {
    let key = format!("{name}=\"");
    let Some(i) = open.find(&key) else {
        return String::new();
    };
    let start = i + key.len();
    let Some(end_rel) = open[start..].find('"') else {
        return String::new();
    };
    open[start..start + end_rel].to_string()
}

fn column_index(open: &str) -> usize {
    let reference = attr(open, "r");
    let mut n = 0i32;
    for ch in reference.chars() {
        if ch.is_ascii_alphabetic() {
            n = n * 26 + (ch.to_ascii_uppercase() as i32 - 'A' as i32 + 1);
        } else {
            break;
        }
    }
    (n - 1).max(0) as usize
}
