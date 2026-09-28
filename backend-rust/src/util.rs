use std::time::{SystemTime, UNIX_EPOCH};

use regex::Regex;
use serde::Deserialize;

use crate::error::AppError;

pub fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

pub fn now_text() -> String {
    chrono::Local::now().format("%Y-%m-%d %H:%M:%S").to_string()
}

pub fn today_start() -> String {
    chrono::Local::now().format("%Y-%m-%d 00:00:00").to_string()
}

pub fn normalize_dt(value: Option<String>) -> Option<String> {
    let raw = value?;
    let text = raw.trim().replace('T', " ");
    let text = text.split_once('.').map(|(head, _)| head).unwrap_or(&text);
    let text = text.trim();
    if text.is_empty() {
        None
    } else {
        Some(text.to_string())
    }
}

pub fn blank_to_none(value: Option<String>) -> Option<String> {
    value.and_then(|s| {
        let t = s.trim();
        if t.is_empty() {
            None
        } else {
            Some(t.to_string())
        }
    })
}

pub fn lower_email(value: Option<String>) -> Option<String> {
    blank_to_none(value).map(|s| s.to_lowercase())
}

pub fn like_pat(keyword: &str) -> String {
    format!("%{}%", keyword.trim().to_lowercase())
}

pub fn tombstone(value: &str, id: i64, max_len: usize) -> String {
    let suffix = format!("__del_{id}");
    if value.trim().is_empty() {
        return truncate(&suffix, max_len);
    }
    if value.len() + suffix.len() <= max_len {
        return format!("{value}{suffix}");
    }
    let keep = max_len.saturating_sub(suffix.len());
    format!("{}{suffix}", &value[..keep.min(value.len())])
}

fn truncate(value: &str, max_len: usize) -> String {
    if value.len() <= max_len {
        value.to_string()
    } else {
        value[..max_len].to_string()
    }
}

pub fn require_text(value: &Option<String>, message: &str) -> Result<String, AppError> {
    match blank_to_none(value.clone()) {
        Some(v) => Ok(v),
        None => Err(AppError::bad(message)),
    }
}

pub fn max_chars(value: &str, max: usize, message: &str) -> Result<(), AppError> {
    if value.chars().count() > max {
        Err(AppError::bad(message))
    } else {
        Ok(())
    }
}

pub fn opt_max(value: &Option<String>, max: usize, message: &str) -> Result<(), AppError> {
    if let Some(v) = value {
        max_chars(v, max, message)?;
    }
    Ok(())
}

pub fn matches(value: &str, pattern: &str, message: &str) -> Result<(), AppError> {
    let re = Regex::new(pattern).expect("regex");
    if re.is_match(value) {
        Ok(())
    } else {
        Err(AppError::bad(message))
    }
}

pub fn parse_csv_line(line: &str) -> Vec<String> {
    let mut cols = Vec::new();
    let mut cur = String::new();
    let mut in_quotes = false;
    let chars: Vec<char> = line.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        if in_quotes {
            if c == '"' {
                if i + 1 < chars.len() && chars[i + 1] == '"' {
                    cur.push('"');
                    i += 1;
                } else {
                    in_quotes = false;
                }
            } else {
                cur.push(c);
            }
        } else if c == '"' {
            in_quotes = true;
        } else if c == ',' {
            cols.push(std::mem::take(&mut cur));
        } else {
            cur.push(c);
        }
        i += 1;
    }
    cols.push(cur);
    cols
}

pub fn neutralize(value: &str) -> String {
    if value.is_empty() {
        return String::new();
    }
    match value.chars().next() {
        Some('=' | '+' | '-' | '@' | '\t' | '\r') => format!("'{value}"),
        _ => value.to_string(),
    }
}

pub fn csv_escape(value: &str) -> String {
    let raw = value;
    let v = neutralize(raw);
    let formula = v != raw;
    if formula || v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r') {
        format!("\"{}\"", v.replace('"', "\"\""))
    } else {
        v
    }
}

pub fn guess_location(ip: &str) -> String {
    let v = ip.trim();
    if v.is_empty() || v.eq_ignore_ascii_case("unknown") {
        return String::new();
    }
    if v == "127.0.0.1" || v == "::1" || v == "0:0:0:0:0:0:0:1" || v == "https://example.net/1" {
        return "本机".into();
    }
    if v.starts_with("10.") || v.starts_with("192.168.") || v.starts_with("169.254.") || v.starts_with("fe80:")
    {
        return "内网".into();
    }
    if let Some(rest) = v.strip_prefix("172.") {
        if let Some(second) = rest.split('.').next() {
            if let Ok(n) = second.parse::<i32>() {
                if (16..=31).contains(&n) {
                    return "内网".into();
                }
            }
        }
    }
    String::new()
}

pub fn parse_browser(ua: &str) -> String {
    if ua.trim().is_empty() {
        return "未知".into();
    }
    let lower = ua.to_lowercase();
    if lower.contains("edg/") {
        "Edge".into()
    } else if lower.contains("firefox/") {
        "Firefox".into()
    } else if lower.contains("chrome/") {
        "Chrome".into()
    } else if lower.contains("safari/") {
        "Safari".into()
    } else {
        "未知".into()
    }
}

pub fn parse_os(ua: &str) -> String {
    if ua.trim().is_empty() {
        return "未知".into();
    }
    let lower = ua.to_lowercase();
    if lower.contains("windows") {
        "Windows".into()
    } else if lower.contains("mac os") {
        "macOS".into()
    } else if lower.contains("android") {
        "Android".into()
    } else if lower.contains("iphone") || lower.contains("ios") {
        "iOS".into()
    } else if lower.contains("linux") {
        "Linux".into()
    } else {
        "未知".into()
    }
}

pub fn client_ip(trusted: bool, forwarded: Option<&str>, real_ip: Option<&str>, peer: &str) -> String {
    if trusted {
        if let Some(ip) = forwarded {
            let ip = ip.trim();
            if !ip.is_empty() && !ip.eq_ignore_ascii_case("unknown") {
                return ip.split(',').next().unwrap_or(ip).trim().to_string();
            }
        }
        if let Some(ip) = real_ip {
            let ip = ip.trim();
            if !ip.is_empty() && !ip.eq_ignore_ascii_case("unknown") {
                return ip.to_string();
            }
        }
    }
    if peer.is_empty() {
        "unknown".into()
    } else {
        peer.to_string()
    }
}

pub fn mask_secrets(json: &str) -> String {
    let re = Regex::new(
        r#"(?i)("(?:password|oldPassword|newPassword|refreshToken|token|accessToken)"\s*:\s*")[^"]*(")"#,
    )
    .expect("mask");
    re.replace_all(json, "$1***$2").to_string()
}

pub fn clip(value: &str, max: usize) -> String {
    if value.chars().count() <= max {
        value.to_string()
    } else {
        let clipped: String = value.chars().take(max).collect();
        format!("{clipped}...")
    }
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PageParams {
    #[serde(default)]
    pub page_num: Option<i64>,
    #[serde(default)]
    pub page_size: Option<i64>,
    #[serde(default)]
    pub order_by: Option<String>,
    #[serde(default)]
    pub sort_direction: Option<String>,
}

impl PageParams {
    pub fn num(&self) -> i64 {
        match self.page_num {
            Some(n) if n >= 1 => n,
            _ => 1,
        }
    }

    pub fn size(&self) -> i64 {
        match self.page_size {
            None => 10,
            Some(n) if n < 1 => 1,
            Some(n) => n.min(200),
        }
    }

    pub fn offset(&self) -> i64 {
        (self.num() - 1) * self.size()
    }

    /// `default` is used only when the client omits orderBy.
    /// An illegal orderBy is ignored, matching PageQuery.toPageable.
    pub fn order_sql(&self, allowed: &[(&str, &str)], default_col: &str, default_desc: bool) -> String {
        if let Some(raw) = self.order_by.as_deref() {
            let key = raw.trim();
            if key.is_empty() {
                return String::new();
            }
            if key.eq_ignore_ascii_case("password")
                || key.eq_ignore_ascii_case("pwdReset")
                || key.eq_ignore_ascii_case("pwd_reset")
            {
                return String::new();
            }
            if let Some((_, col)) = allowed.iter().find(|(name, _)| name.eq_ignore_ascii_case(key)) {
                let desc = self
                    .sort_direction
                    .as_deref()
                    .unwrap_or("asc")
                    .eq_ignore_ascii_case("desc");
                return format!(" ORDER BY {col} {}", if desc { "DESC" } else { "ASC" });
            }
            return String::new();
        }
        format!(
            " ORDER BY {default_col} {}",
            if default_desc { "DESC" } else { "ASC" }
        )
    }
}

pub fn assert_acyclic(id: Option<i64>, parent_id: i64, mut parent_of: impl FnMut(i64) -> Result<i64, AppError>) -> Result<(), AppError> {
    if parent_id == 0 {
        return Ok(());
    }
    let mut cursor = parent_id;
    for _ in 0..64 {
        if cursor == 0 {
            return Ok(());
        }
        if id == Some(cursor) {
            return Err(AppError::bad("不能将子级设为父级"));
        }
        cursor = parent_of(cursor)?;
    }
    Err(AppError::bad("层级过深或存在环"))
}

pub fn ancestor_hit(ancestors: &str, dept_id: i64) -> bool {
    let needle = dept_id.to_string();
    ancestors
        .split(',')
        .map(str::trim)
        .any(|part| part == needle)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn csv_roundtrip_quotes() {
        let line = r#"a,"b,c","d""e""#;
        let cols = parse_csv_line(line);
        assert_eq!(cols, vec!["a", "b,c", "d\"e"]);
    }

    #[test]
    fn tombstone_fits() {
        assert_eq!(tombstone("admin", 3, 50), "admin__del_3");
        assert_eq!(tombstone("abcdefghijklmnopqrstuvwxyz", 1, 10).len(), 10);
    }

    #[test]
    fn page_clamps() {
        let q = PageParams {
            page_num: Some(0),
            page_size: Some(500),
            order_by: Some("password".into()),
            sort_direction: Some("desc".into()),
        };
        assert_eq!(q.num(), 1);
        assert_eq!(q.size(), 200);
        assert!(q.order_sql(&[("id", "id")], "id", true).is_empty());
    }
}
