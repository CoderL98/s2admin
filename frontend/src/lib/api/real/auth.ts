/**
 * 认证 API —— 真实后端实现
 */
import { get, post, put, del, upload, setToken, setRefreshToken, removeToken, getRefreshToken } from '$lib/utils/request';
import type {
	CaptchaResult,
	ChangePasswordPayload,
	LoginForm,
	LoginResult,
	LoginSession,
	LoginUser,
	PageQuery,
	PageResult,
	ProfilePayload
} from '$lib/types/auth';
import type { FileVO, LoginLog, Menu, OperationLog } from '$lib/types/entities';

export async function login(form: LoginForm): Promise<LoginResult> {
	const result = await post<LoginResult>('/api/auth/login', form, {
		redirectOnExpired: false,
		withToken: false,
		skipRefresh: true
	});
	setToken(result.token);
	setRefreshToken(result.refreshToken);
	return result;
}

export async function logout(): Promise<void> {
	try {
		await post<void>('/api/auth/logout', { refreshToken: getRefreshToken() ?? undefined });
	} finally {
		removeToken();
	}
}

export async function getUserInfo(): Promise<LoginUser> {
	return get<LoginUser>('/api/auth/info');
}

export async function refresh(refreshToken: string): Promise<LoginResult> {
	return post<LoginResult>('/api/auth/refresh', { refreshToken }, { skipRefresh: true, withToken: false });
}

export async function getUserMenus(): Promise<Menu[]> {
	return get<Menu[]>('/api/auth/menus');
}

export async function oauthProviders(): Promise<{ id: string; name: string }[]> {
	return get<{ id: string; name: string }[]>('/api/auth/oauth/providers', undefined, { withToken: false });
}

export async function getCaptcha(): Promise<CaptchaResult> {
	return get<CaptchaResult>('/api/auth/captcha', undefined, { withToken: false });
}

export async function updateProfile(data: ProfilePayload): Promise<LoginUser> {
	return put<LoginUser>('/api/auth/profile', data);
}

export async function changePassword(data: ChangePasswordPayload): Promise<void> {
	return put<void>('/api/auth/password', data);
}

export async function forgotPassword(
	account: string,
	captcha?: string,
	captchaKey?: string
): Promise<{ message: string; mockCode?: string }> {
	return post<{ message: string; mockCode?: string }>(
		'/api/auth/forgot-password',
		{ account, captcha, captchaKey },
		{
			withToken: false,
			redirectOnExpired: false
		}
	);
}

export async function resetPassword(account: string, code: string, newPassword: string): Promise<void> {
	return post<void>(
		'/api/auth/reset-password',
		{ account, code, newPassword },
		{ withToken: false, redirectOnExpired: false }
	);
}

export async function uploadAvatar(file: File): Promise<FileVO> {
	const fd = new FormData();
	fd.append('file', file);
	return upload<FileVO>('/api/auth/avatar', fd);
}

export async function getMySessions(): Promise<LoginSession[]> {
	return get<LoginSession[]>('/api/auth/sessions');
}

export async function kickSession(sid: string): Promise<void> {
	return del<void>('/api/auth/sessions/' + encodeURIComponent(sid));
}

export async function kickOtherSessions(): Promise<void> {
	return del<void>('/api/auth/sessions/others');
}

export async function getMyLoginLogs(q: PageQuery): Promise<PageResult<LoginLog>> {
	return get<PageResult<LoginLog>>('/api/auth/my-login-logs', {
		pageNum: q.pageNum,
		pageSize: q.pageSize
	});
}

export async function getMyOperations(q: PageQuery): Promise<PageResult<OperationLog>> {
	return get<PageResult<OperationLog>>('/api/auth/my-operations', {
		pageNum: q.pageNum,
		pageSize: q.pageSize
	});
}
