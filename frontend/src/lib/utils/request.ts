/**
 * API 请求封装模块
 * 基于 Fetch API 封装，统一处理请求/响应
 * 所有请求自动携带 Token，支持 Token 刷新
 */
import { env } from '$env/dynamic/public';

// API 基础地址 - 从环境变量读取
const API_BASE_URL = env.PUBLIC_API_BASE_URL || 'http://localhost:8080';

// Token 存储键名 - 从环境变量读取
const TOKEN_KEY = env.PUBLIC_TOKEN_KEY || 's2admin_token';
const REFRESH_TOKEN_KEY = env.PUBLIC_REFRESH_TOKEN_KEY || 's2admin_refresh_token';

// 响应码定义
export const ResultCode = {
  SUCCESS: 200,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  INTERNAL_ERROR: 500,
} as const;

export type ResultCodeType = (typeof ResultCode)[keyof typeof ResultCode];

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
 * 检查响应是否成功
 */
function checkStatus(response: Response): void {
  if (!response.ok) {
    throw new ApiError(response.status, `请求失败: ${response.status} ${response.statusText}`);
  }
}

/**
 * 解析响应数据
 */
async function parseResponse<T>(response: Response): Promise<T> {
  const contentType = response.headers.get('content-type');
  if (contentType?.includes('application/json')) {
    const data = await response.json();
    return data as T;
  }
  return (await response.text()) as unknown as T;
}

/**
 * 处理 API 响应
 */
function handleApiResponse<T>(response: { code: number; message: string; data: T }): T {
  if (response.code !== ResultCode.SUCCESS) {
    throw new ApiError(response.code, response.message, response.data);
  }
  return response.data;
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
    ...fetchConfig
  } = config;

  // 构建完整 URL
  const url = endpoint.startsWith('http')
    ? endpoint
    : `${API_BASE_URL}${endpoint}`;

  // 构建请求头
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...fetchConfig.headers,
  };

  // 自动携带 Token
  if (withToken) {
    const token = getToken();
    if (token) {
      (headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
    }
  }

  // 创建 AbortController 用于超时控制
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);

  try {
    const response = await fetch(url, {
      ...fetchConfig,
      headers,
      signal: controller.signal,
    });

    clearTimeout(timeoutId);
    checkStatus(response);

    const result = await parseResponse<{ code: number; message: string; data: T }>(response);
    return handleApiResponse(result);
  } catch (error) {
    clearTimeout(timeoutId);

    if (error instanceof ApiError) {
      throw error;
    }

    if (error instanceof Error) {
      if (error.name === 'AbortError') {
        throw new ApiError(ResultCode.INTERNAL_ERROR, '请求超时，请稍后重试');
      }
      throw new ApiError(ResultCode.INTERNAL_ERROR, error.message);
    }

    throw new ApiError(ResultCode.INTERNAL_ERROR, '网络请求失败');
  }
}

/**
 * GET 请求
 */
export async function get<T>(
  endpoint: string,
  params?: Record<string, string | number | undefined>,
  config?: RequestConfig
): Promise<T> {
  let url = endpoint;
  if (params) {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) {
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
    body: data ? JSON.stringify(data) : undefined,
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
    body: data ? JSON.stringify(data) : undefined,
  });
}

/**
 * DELETE 请求
 */
export async function del<T>(
  endpoint: string,
  config?: RequestConfig
): Promise<T> {
  return request<T>(endpoint, { ...config, method: 'DELETE' });
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
    body: formData,
    headers: {
      ...config?.headers,
    },
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
  ResultCode,
  ApiError,
};
