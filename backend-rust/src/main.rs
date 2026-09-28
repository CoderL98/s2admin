#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "s2admin_rs=info,tower_http=info".into()))
        .init();
    let config = s2admin_rs::AppConfig::from_env();
    s2admin_rs::serve(config).await
}
