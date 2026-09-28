/**
 * API 请求封装模块
 * 基于 Fetch API 封装,统一处理请求/响应
 * - 自动携带 Token
 * - 401 时自动使用 RefreshToken 刷新并重试一次(并发请求共享一次刷新)
 * - 支持解析非 2xx 响应体中的后端 Result 错误信息
 * - 统一抛 ApiError
 */
import { env } from '$env/dynamic/public';
import { USE_MOCK } from '$lib/config';

// API 基础地址 - 从环境变量读取
const API_BASE_URL = env.PUBLIC_API_BASE_URL || 'http://localhost:8080';

// Token 存储键名 - 从环境变量读取
const TOKEN_KEY = env.PUBLIC_TOKEN_KEY || 's2admin_token';
const REFRESH_TOKEN_KEY = env.PUBLIC_REFRESH_TOKEN_KEY || 's2admin_refresh_token';

// 响应码定义(与后端 ResultCode 一致)
export const ResultCode = {
	SUCCESS: 200,
	BAD_REQUEST: 400,
	UNAUTHORIZED: 401,
	FORBIDDEN: 403,
	NOT_FOUND: 404,
	TOO_MANY_REQUESTS: 429,
	INTERNAL_ERROR: 500
} as const;

export type ResultCodeType = (typeof ResultCode)[keyof typeof ResultCode];

/** 后端统一响应结构 */
export interface ApiResult<T = unknown> {
	code: number;
	message: string;
	data: T;
	timestamp: number;
}

/**
 * 获取存储的 Token
 */
export function getToken(): string | null {
	if (typeof localStorage === 'undefined') return null;
	return localStorage.getItem(TOKEN_KEY);
}

/**
 * 设置 Token
 */
export function setToken(token: string): void {
	if (typeof localStorage !== 'undefined') {
		localStorage.setItem(TOKEN_KEY, token);
	}
}

/**
 * 移除 Token
 */
export function removeToken(): void {
	if (typeof localStorage !== 'undefined') {
		localStorage.removeItem(TOKEN_KEY);
		localStorage.removeItem(REFRESH_TOKEN_KEY);
	}
}

/**
 * 获取 RefreshToken
 */
export function getRefreshToken(): string | null {
	if (typeof localStorage === 'undefined') return null;
	return localStorage.getItem(REFRESH_TOKEN_KEY);
}

/**
 * 设置 RefreshToken
 */
export function setRefreshToken(token: string): void {
	if (typeof localStorage !== 'undefined') {
		localStorage.setItem(REFRESH_TOKEN_KEY, token);
	}
}

/**
 * 请求接口基础配置
 */
interface RequestConfig extends RequestInit {
	/** 是否自动携带 Token */
	withToken?: boolean;
	/** 请求超时时间(ms) */
	timeout?: number;
	/** 是否跳过 401 自动刷新(默认 false;刷新接口本身传 true) */
	skipRefresh?: boolean;
	/** 是否在刷新失败后跳转登录页(默认 true) */
	redirectOnExpired?: boolean;
}

/**
 * 自定义错误类
 */
export class ApiError extends Error {
	constructor(
		public code: number,
		message: string,
		public data?: unknown
	) {
		super(message);
		this.name = 'ApiError';
	}
}

/**
 * 尝试解析响应体(JSON 优先,失败回退文本)
 */
async function parseBody(response: Response): Promise<ApiResult | string | null> {
	const contentType = response.headers.get('content-type') ?? '';
	if (contentType.includes('application/json')) {
		try {
			return (await response.json()) as ApiResult;
		} catch {
			return null;
		}
	}
	const text = await response.text();
	return text || null;
}

/**
 * 从响应中提取错误信息并抛出 ApiError
 */
function throwFromResponse(response: Response, body: ApiResult | string | null): never {
	if (body && typeof body === 'object' && 'code' in body && 'message' in body) {
		throw new ApiError(body.code, body.message || `请求失败: ${response.status}`, body.data);
	}
	throw new ApiError(response.status, `请求失败: ${response.status} ${response.statusText}`);
}

/**
 * 解析业务响应:HTTP 200 但 code != 200 也视为失败
 */
function handleApiResponse<T>(body: ApiResult<T> | string | null): T {
	if (!body || typeof body !== 'object' || !('code' in body)) {
		throw new ApiError(ResultCode.INTERNAL_ERROR, '响应格式不正确');
	}
	if (body.code !== ResultCode.SUCCESS) {
		if (isMustChangePassword(body, body.code)) {
			mustChangePasswordHandler?.();
		}
		throw new ApiError(body.code, body.message, body.data);
	}
	return body.data;
}

/**
 * 401 过期处理器(由 auth store 注册,用于清理内存状态并跳转)
 */
let authExpiredHandler: (() => void) | null = null;
export function setAuthExpiredHandler(handler: (() => void) | null): void {
	authExpiredHandler = handler;
}

let mustChangePasswordHandler: (() => void) | null = null;
export function setMustChangePasswordHandler(handler: (() => void) | null): void {
	mustChangePasswordHandler = handler;
}

function notifyAuthExpired() {
	removeToken();
	authExpiredHandler?.();
}

function isMustChangePassword(body: ApiResult | string | null, status: number): boolean {
	if (status !== ResultCode.FORBIDDEN) return false;
	if (body && typeof body === 'object' && 'message' in body) {
		return String(body.message).includes('请先修改初始密码');
	}
	return false;
}

/** 正在进行的刷新任务(并发请求共享) */
let refreshing: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
	const refreshToken = getRefreshToken();
	if (!refreshToken) {
		throw new ApiError(ResultCode.UNAUTHORIZED, '登录已过期,请重新登录');
	}
	if (!refreshing) {
		refreshing = (async () => {
			if (USE_MOCK) {
				const current = getToken();
				if (!current) {
					throw new ApiError(ResultCode.UNAUTHORIZED, '登录已过期,请重新登录');
				}
				return current;
			}
			const res = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ refreshToken })
			});
			const body = (await parseBody(res)) as ApiResult<{ token: string; refreshToken: string }> | null;
			if (!res.ok || !body || body.code !== ResultCode.SUCCESS || !body.data?.token) {
				throw new ApiError(
					body && typeof body === 'object' && 'code' in body ? body.code : res.status,
					body && typeof body === 'object' && 'message' in body ? body.message : '登录已过期,请重新登录'
				);
			}
			setToken(body.data.token);
			setRefreshToken(body.data.refreshToken);
			return body.data.token;
		})().finally(() => {
			refreshing = null;
		});
	}
	return refreshing;
}

/**
 * 发起 API 请求
 * @param endpoint API 端点 (如: /api/auth/login)
 * @param config 请求配置
 */
export async function request<T>(
	endpoint: string,
	config: RequestConfig = {}
): Promise<T> {
	const {
		withToken = true,
		timeout = 30000,
		skipRefresh = false,
		redirectOnExpired = true,
		...fetchConfig
	} = config;

	// 构建完整 URL
	const url = endpoint.startsWith('http') ? endpoint : `${API_BASE_URL}${endpoint}`;

	let retried = false;
	const doFetch = async (tokenOverride?: string): Promise<T> => {
		// 构建请求头
		const headers: HeadersInit = {
			...fetchConfig.headers
		};
		if (fetchConfig.body && !(fetchConfig.body instanceof FormData)) {
			(headers as Record<string, string>)['Content-Type'] ??= 'application/json';
		}
		if (withToken) {
			const token = tokenOverride ?? getToken();
			if (token) {
				(headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
			}
			const tenant = typeof localStorage === 'undefined' ? null : localStorage.getItem('s2admin.tenant');
			if (tenant && /^\d+$/.test(tenant)) {
				(headers as Record<string, string>)['X-Tenant-Id'] = tenant;
			}
		}

		// 创建 AbortController 用于超时控制
		const controller = new AbortController();
		const timeoutId = setTimeout(() => controller.abort(), timeout);
		try {
			const response = await fetch(url, {
				...fetchConfig,
				headers,
				signal: controller.signal
			});
			const body = await parseBody(response);

			if (!response.ok) {
				if (isMustChangePassword(body as ApiResult | string | null, response.status)) {
					mustChangePasswordHandler?.();
					throwFromResponse(response, body);
				}
				// 401 且允许刷新且存在 refreshToken 时,刷新后重试一次
				if (
					!retried &&
					response.status === ResultCode.UNAUTHORIZED &&
					!skipRefresh &&
					withToken &&
					getRefreshToken()
				) {
					retried = true;
					const newToken = await refreshAccessToken();
					return await doFetch(newToken);
				}
				if (redirectOnExpired && response.status === ResultCode.UNAUTHORIZED) {
					notifyAuthExpired();
				}
				throwFromResponse(response, body);
			}
			return handleApiResponse<T>(body as ApiResult<T> | null);
		} catch (error) {
			if (error instanceof ApiError) {
				// 仅「HTTP 401 触发过刷新」后仍失败才清会话;HTTP 200 + 业务 401 不当过期
				if (
					retried &&
					error.code === ResultCode.UNAUTHORIZED &&
					redirectOnExpired &&
					!skipRefresh
				) {
					notifyAuthExpired();
				}
				throw error;
			}
			if (error instanceof Error) {
				if (error.name === 'AbortError') {
					throw new ApiError(ResultCode.INTERNAL_ERROR, '请求超时,请稍后重试');
				}
				throw new ApiError(ResultCode.INTERNAL_ERROR, error.message);
			}
			throw new ApiError(ResultCode.INTERNAL_ERROR, '网络请求失败');
		} finally {
			clearTimeout(timeoutId);
		}
	};

	return doFetch();
}

/**
 * 带 Token 的原始请求。401 时刷新一次再重试,供下载、图片等非 JSON 接口使用。
 */
export async function authorizedFetch(url: string, init: RequestInit = {}): Promise<Response> {
	const headers = new Headers(init.headers);
	const token = getToken();
	if (token && !headers.has('Authorization')) {
		headers.set('Authorization', `Bearer ${token}`);
	}
	let response = await fetch(url, { ...init, headers });
	if (response.status === ResultCode.UNAUTHORIZED && getRefreshToken()) {
		try {
			const newToken = await refreshAccessToken();
			headers.set('Authorization', `Bearer ${newToken}`);
			response = await fetch(url, { ...init, headers });
		} catch {
			notifyAuthExpired();
			return response;
		}
	}
	if (response.status === ResultCode.UNAUTHORIZED) {
		notifyAuthExpired();
	}
	return response;
}

/**
 * GET 请求
 */
export async function get<T>(
	endpoint: string,
	params?: Record<string, string | number | boolean | undefined | (string | number)[]>,
	config?: RequestConfig
): Promise<T> {
	let url = endpoint;
	if (params) {
		const searchParams = new URLSearchParams();
		Object.entries(params).forEach(([key, value]) => {
			if (value === undefined || value === null) return;
			if (Array.isArray(value)) {
				value.forEach((v) => searchParams.append(key, String(v)));
			} else {
				searchParams.append(key, String(value));
			}
		});
		const queryString = searchParams.toString();
		if (queryString) {
			url += `?${queryString}`;
		}
	}
	return request<T>(url, { ...config, method: 'GET' });
}

/**
 * POST 请求
 */
export async function post<T>(
	endpoint: string,
	data?: unknown,
	config?: RequestConfig
): Promise<T> {
	return request<T>(endpoint, {
		...config,
		method: 'POST',
		body: data === undefined ? undefined : JSON.stringify(data)
	});
}

/**
 * PUT 请求
 */
export async function put<T>(
	endpoint: string,
	data?: unknown,
	config?: RequestConfig
): Promise<T> {
	return request<T>(endpoint, {
		...config,
		method: 'PUT',
		body: data === undefined ? undefined : JSON.stringify(data)
	});
}

/**
 * DELETE 请求(支持 query 参数)
 */
export async function del<T>(
	endpoint: string,
	params?: Record<string, string | number | boolean | undefined | (string | number)[]>,
	config?: RequestConfig
): Promise<T> {
	let url = endpoint;
	if (params) {
		const searchParams = new URLSearchParams();
		Object.entries(params).forEach(([key, value]) => {
			if (value === undefined || value === null) return;
			if (Array.isArray(value)) {
				value.forEach((v) => searchParams.append(key, String(v)));
			} else {
				searchParams.append(key, String(value));
			}
		});
		const queryString = searchParams.toString();
		if (queryString) {
			url += `?${queryString}`;
		}
	}
	return request<T>(url, { ...config, method: 'DELETE' });
}

/**
 * 文件上传请求 (不设置 Content-Type)
 */
export async function upload<T>(
	endpoint: string,
	formData: FormData,
	config?: RequestConfig
): Promise<T> {
	return request<T>(endpoint, {
		...config,
		withToken: config?.withToken !== false,
		method: 'POST',
		body: formData
	});
}

export default {
	get,
	post,
	put,
	del,
	upload,
	request,
	getToken,
	setToken,
	removeToken,
	getRefreshToken,
	setRefreshToken,
	setAuthExpiredHandler,
	setMustChangePasswordHandler,
	ResultCode,
	ApiError
};