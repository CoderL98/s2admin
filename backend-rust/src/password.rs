use rand::seq::SliceRandom;
use rand::Rng;

use crate::error::AppError;

pub const POLICY_MESSAGE: &str = "密码至少 8 位,且须包含大小写字母、数字和特殊字符";

pub fn validate(password: &str) -> Result<(), AppError> {
    if !is_strong(password) {
        Err(AppError::bad(POLICY_MESSAGE))
    } else {
        Ok(())
    }
}

pub fn is_strong(password: &str) -> bool {
    if password.trim().is_empty() {
        return false;
    }
    let len = password.chars().count();
    if !(8..=64).contains(&len) {
        return false;
    }
    let mut lower = false;
    let mut upper = false;
    let mut digit = false;
    let mut special = false;
    for ch in password.chars() {
        if ch.is_ascii_lowercase() {
            lower = true;
        } else if ch.is_ascii_uppercase() {
            upper = true;
        } else if ch.is_ascii_digit() {
            digit = true;
        } else {
            special = true;
        }
    }
    lower && upper && digit && special
}

pub fn validate_length_then_policy(password: &str) -> Result<(), AppError> {
    let len = password.chars().count();
    if !(8..=64).contains(&len) {
        return Err(AppError::bad("密码长度需在 8-64 之间"));
    }
    validate(password)
}

pub fn random_strong() -> String {
    let mut rng = rand::thread_rng();
    let lower = "abcdefghijkmnopqrstuvwxyz";
    let upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    let digits = "23456789";
    let special = "!@#$%^&*?";
    let all = format!("{lower}{upper}{digits}{special}");
    let pick = |src: &str, rng: &mut rand::rngs::ThreadRng| {
        src.chars().nth(rng.gen_range(0..src.chars().count())).unwrap()
    };
    let mut chars = vec![
        pick(lower, &mut rng),
        pick(upper, &mut rng),
        pick(digits, &mut rng),
        pick(special, &mut rng),
    ];
    for _ in 4..12 {
        chars.push(pick(&all, &mut rng));
    }
    chars.shuffle(&mut rng);
    chars.into_iter().collect()
}

pub fn hash(password: &str) -> Result<String, AppError> {
    bcrypt::hash(password, bcrypt::DEFAULT_COST).map_err(|e| AppError::Internal(e.to_string()))
}

pub fn verify(password: &str, hashed: &str) -> bool {
    bcrypt::verify(password, hashed).unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_weak_and_accepts_strong() {
        assert!(validate("admin123").is_err());
        assert!(validate("Admin123!").is_ok());
        assert!(is_strong(&random_strong()));
    }
}
