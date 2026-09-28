/**
 * 认证 store —— Svelte 5 Runes 形式
 * 全局共享当前登录用户、token 与可见菜单
 */
import { goto } from '$app/navigation';
import {
	ApiError,
	ResultCode,
	getToken,
	removeToken,
	setAuthExpiredHandler,
	setMustChangePasswordHandler,
	setRefreshToken,
	setToken
} from '$lib/utils/request';
import * as api from '$lib/api/auth';
import type { LoginUser } from '$lib/types/auth';
import type { Menu } from '$lib/types/entities';

class AuthStore {
	token = $state<string | null>(null);
	currentUser = $state<LoginUser | null>(null);
	menus = $state<Menu[]>([]);
	loading = $state(false);
	hydrating = $state(false);
	ready = $state(false);

	constructor() {
		if (typeof window !== 'undefined') {
			this.token = getToken();
			setAuthExpiredHandler(() => {
				this.clearSession();
				goto('/login');
			});
			setMustChangePasswordHandler(() => {
				if (typeof window !== 'undefined' && window.location.pathname !== '/profile') {
					goto('/profile');
				}
			});
		}
	}

	isLoggedIn = $derived(this.token !== null);

	hasPermission(code: string): boolean {
		const user = this.currentUser;
		if (!user || !code) return false;
		if (user.roles?.includes('SUPER_ADMIN') || user.permissions?.includes('*')) return true;
		return user.permissions?.includes(code) ?? false;
	}

	canAccess(path: string): boolean {
		if (path === '/dashboard' || path === '/login' || path === '/profile' || path === '/message')
			return true;
		if (this.hasPermission('*')) return true;
		// 只认菜单上的精确路径。父目录 /system 不能把 /system/user 一并放行。
		return this.collectPaths(this.menus).some((url) => url === path);
	}

	async login(
		username: string,
		password: string,
		captcha?: string,
		captchaKey?: string,
		rememberMe?: boolean
	) {
		this.loading = true;
		try {
			const r = await api.login({ username, password, captcha, captchaKey, rememberMe });
			this.token = r.token;
			this.currentUser = r.user;
			this.menus = await api.getUserMenus().catch(() => []);
			return r;
		} finally {
			this.loading = false;
		}
	}

	async acceptTokens(token: string, refreshToken: string) {
		this.loading = true;
		try {
			setToken(token);
			setRefreshToken(refreshToken);
			this.token = token;
			this.currentUser = await api.getUserInfo();
			this.menus = await api.getUserMenus().catch(() => []);
		} finally {
			this.loading = false;
		}
	}

	async hydrate() {
		if (this.ready || this.hydrating) return;
		this.hydrating = true;
		try {
			if (!this.token) {
				this.ready = true;
				return;
			}
			this.currentUser = await api.getUserInfo();
			this.menus = await api.getUserMenus().catch(() => []);
		} catch (e) {
			if (e instanceof ApiError && e.code === ResultCode.UNAUTHORIZED) {
				this.clearSession();
			}
		} finally {
			this.hydrating = false;
			this.ready = true;
		}
	}

	async fetchUserInfo() {
		if (!this.token) return null;
		this.currentUser = await api.getUserInfo();
		return this.currentUser;
	}

	async logout() {
		try {
			await api.logout();
		} finally {
			this.endSession();
		}
	}

	/** 本地结束会话,不再打登出接口(改密后 Token 已作废) */
	endSession() {
		this.clearSession();
		return goto('/login');
	}

	private clearSession() {
		this.token = null;
		this.currentUser = null;
		this.menus = [];
		removeToken();
	}

	private collectPaths(nodes: Menu[]): string[] {
		const urls: string[] = [];
		for (const node of nodes) {
			if (node.path && node.type === 2 && !/^https?:\/\//i.test(node.path)) urls.push(node.path);
			if (node.children?.length) urls.push(...this.collectPaths(node.children));
		}
		return urls;
	}
}

export const authStore = new AuthStore();
