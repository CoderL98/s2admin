/**
 * 全局配置
 */
import { env } from '$env/dynamic/public';

/** 是否启用 Mock 数据(未配置时默认 false,与 .env 模板一致) */
export const USE_MOCK = (env.PUBLIC_USE_MOCK ?? 'false') === 'true';

/** 应用名称 */
export const APP_NAME = env.PUBLIC_APP_NAME || 'S2Admin';

/** 默认分页条数 */
export const DEFAULT_PAGE_SIZE = Number(env.PUBLIC_DEFAULT_PAGE_SIZE || 10);
