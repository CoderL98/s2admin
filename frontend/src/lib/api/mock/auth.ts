/**
 * 认证相关 API —— mock 实现
 * 写死 admin / admin123 → 假 token
 */
import { delay } from '$lib/mock/_helpers';
import { setToken, removeToken, setRefreshToken } from '$lib/utils/request';
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
import { mockMenus } from '$lib/mock/menus';
import { buildTree } from '$lib/utils/tree';

const FAKE_TOKEN = 'mock-jwt-token-' + Math.random().toString(36).slice(2);
const FAKE_REFRESH = 'mock-refresh-token-' + Math.random().toString(36).slice(2);

const mockUser: LoginUser = {
	id: 1,
	username: 'admin',
	nickname: '超级管理员',
	email: 'admin@example.com',
	avatar: undefined,
	roles: ['SUPER_ADMIN'],
	permissions: ['*']
};

export async function login(form: LoginForm): Promise<LoginResult> {
	await delay(400);
	if (form.captcha && form.captcha.toUpperCase() !== 'A3K7') {
		throw new Error('验证码错误或已过期');
	}
	if (form.username === 'admin' && form.password === 'admin123') {
		setToken(FAKE_TOKEN);
		setRefreshToken(FAKE_REFRESH);
		return {
			token: FAKE_TOKEN,
			refreshToken: FAKE_REFRESH,
			expiresIn: 900,
			user: mockUser
		};
	}
	throw new Error('用户名或密码错误');
}

export async function logout(): Promise<void> {
	await delay(100);
	removeToken();
}

export async function getUserInfo(): Promise<LoginUser> {
	await delay(100);
	return mockUser;
}

export async function refresh(): Promise<LoginResult> {
	await delay(80);
	return {
		token: FAKE_TOKEN,
		refreshToken: FAKE_REFRESH,
		expiresIn: 900,
		user: mockUser
	};
}

export async function getUserMenus(): Promise<Menu[]> {
	await delay(80);
	const visible = mockMenus.filter((m) => m.type !== 3 && m.hidden === 0);
	return buildTree(visible);
}

export async function getCaptcha(): Promise<CaptchaResult> {
	await delay(80);
	return {
		enabled: true,
		captchaKey: 'mock-captcha',
		image:
			"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='120' height='40'><rect width='100%' height='100%' fill='%23f4f4f5'/><text x='28' y='26' font-size='20' font-family='monospace'>A3K7</text></svg>"
	};
}

export async function updateProfile(data: ProfilePayload): Promise<LoginUser> {
	await delay(150);
	mockUser.nickname = data.nickname;
	mockUser.email = data.email ?? mockUser.email;
	mockUser.phone = data.phone;
	mockUser.avatar = data.avatar;
	mockUser.province = data.province;
	mockUser.city = data.city;
	mockUser.district = data.district;
	return { ...mockUser };
}

export async function changePassword(data: ChangePasswordPayload): Promise<void> {
	await delay(150);
	if (data.oldPassword !== 'admin123') {
		throw new Error('原密码不正确');
	}
}

export async function forgotPassword(account: string): Promise<{ message: string; mockCode?: string }> {
	await delay(80);
	if (!account) throw new Error('账号不能为空');
	return { message: '若账号存在且已绑定邮箱,验证码将在有效期内可用', mockCode: '123456' };
}

export async function resetPassword(): Promise<void> {
	await delay(80);
}

export async function uploadAvatar(file: File): Promise<FileVO> {
	await delay(80);
	return { url: URL.createObjectURL(file), name: file.name, size: file.size };
}

const mockSessions: LoginSession[] = [
	{
		sid: 'mock-sid',
		iat: Date.now(),
		ip: '127.0.0.1',
		ua: 'Mozilla/5.0 Mock',
		current: true
	}
];

export async function getMySessions(): Promise<LoginSession[]> {
	await delay(80);
	return [...mockSessions];
}

export async function kickSession(sid: string): Promise<void> {
	await delay(40);
	const i = mockSessions.findIndex((s) => s.sid === sid);
	if (i >= 0 && !mockSessions[i].current) mockSessions.splice(i, 1);
}

export async function kickOtherSessions(): Promise<void> {
	await delay(40);
	for (let i = mockSessions.length - 1; i >= 0; i--) {
		if (!mockSessions[i].current) mockSessions.splice(i, 1);
	}
}

export async function getMyLoginLogs(q: PageQuery): Promise<PageResult<LoginLog>> {
	await delay(80);
	return {
		records: [
			{
				id: 1,
				userId: 1,
				username: 'admin',
				ip: '127.0.0.1',
				location: '本机',
				browser: 'Chrome',
				os: 'Linux',
				status: 0,
				message: '登录成功',
				loginTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
			}
		],
		total: 1,
		size: q.pageSize,
		current: q.pageNum,
		pages: 1
	};
}

export async function oauthProviders(): Promise<{ id: string; name: string }[]> {
	return [];
}

export async function getMyOperations(q: PageQuery): Promise<PageResult<OperationLog>> {
	await delay(80);
	return {
		records: [
			{
				id: 1,
				userId: 1,
				username: 'admin',
				operation: '查询',
				module: '个人中心',
				method: 'GET',
				url: '/api/auth/info',
				ip: '127.0.0.1',
				location: '本机',
				status: 0,
				executeTime: 12,
				operationTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
			}
		],
		total: 1,
		size: q.pageSize,
		current: q.pageNum,
		pages: 1
	};
}