export type Locale = 'zh-CN' | 'en-US';

const zh: Record<string, string> = {
	'login.title': '登录到',
	'login.subtitle': '支持用户名、邮箱或手机号登录',
	'login.account': '账号',
	'login.accountPh': '用户名 / 邮箱 / 手机号',
	'login.password': '密码',
	'login.remember': '记住我（延长登录有效期）',
	'login.captcha': '验证码',
	'login.captchaPh': '点击图片刷新',
	'login.submit': '登录',
	'login.submitting': '登录中...',
	'login.forgot': '忘记密码?',
	'login.hint': '提示',
	'login.oauth': '第三方登录',
	'login.oauthEmpty': '第三方登录未启用。配置微信、Google、微软或 Apple 的客户端后会显示在这里。',
	'login.failed': '登录失败',
	'login.reset': '重置密码',
	'login.resetAccount': '账号(邮箱/用户名/手机号)',
	'login.imageCaptcha': '图形验证码',
	'login.mailCode': '邮箱验证码',
	'login.sendCode': '发送验证码',
	'login.newPassword': '新密码',
	'login.confirmReset': '确认重置',
	'login.back': '返回登录',
	'login.sendFailed': '发送失败',
	'login.resetFailed': '重置失败',
	'header.notices': '最新公告',
	'header.noNotice': '暂无公告',
	'header.guest': '未登录',
	'header.lang': 'EN',
	'tenant.all': '全部租户',
	'oauth.wechat': '微信',
	'oauth.google': 'Google',
	'oauth.microsoft': '微软',
	'oauth.apple': 'Apple'
};

const en: Record<string, string> = {
	'login.title': 'Sign in to',
	'login.subtitle': 'Use your username, email, or phone',
	'login.account': 'Account',
	'login.accountPh': 'Username / email / phone',
	'login.password': 'Password',
	'login.remember': 'Remember me (longer session)',
	'login.captcha': 'Captcha',
	'login.captchaPh': 'Click the image to refresh',
	'login.submit': 'Sign in',
	'login.submitting': 'Signing in...',
	'login.forgot': 'Forgot password?',
	'login.hint': 'Hint',
	'login.oauth': 'Continue with',
	'login.oauthEmpty': 'Social login is off until WeChat, Google, Microsoft, or Apple credentials are configured.',
	'login.failed': 'Sign-in failed',
	'login.reset': 'Reset password',
	'login.resetAccount': 'Account (email / username / phone)',
	'login.imageCaptcha': 'Image captcha',
	'login.mailCode': 'Email code',
	'login.sendCode': 'Send code',
	'login.newPassword': 'New password',
	'login.confirmReset': 'Reset password',
	'login.back': 'Back to sign in',
	'login.sendFailed': 'Could not send the code',
	'login.resetFailed': 'Could not reset the password',
	'header.notices': 'Notices',
	'header.noNotice': 'No notices',
	'header.guest': 'Signed out',
	'header.lang': '中文',
	'tenant.all': 'All tenants',
	'oauth.wechat': 'WeChat',
	'oauth.google': 'Google',
	'oauth.microsoft': 'Microsoft',
	'oauth.apple': 'Apple',
	'route:/dashboard': 'Dashboard',
	'route:/system/user': 'Users',
	'route:/system/role': 'Roles',
	'route:/system/menu': 'Menus',
	'route:/system/permission': 'Permissions',
	'route:/system/config': 'Settings',
	'route:/system/dept': 'Departments',
	'route:/system/file': 'Files',
	'route:/system/notice': 'Notices',
	'route:/system/flow': 'Workflows',
	'route:/system/approval': 'Approvals',
	'route:/system/tenant': 'Tenants',
	'route:/monitor/online': 'Online users',
	'route:/monitor/server': 'Server',
	'route:/monitor/job': 'Jobs',
	'route:/monitor/login-log': 'Login logs',
	'route:/monitor/op-log': 'Operation logs',
	'route:/monitor/error-log': 'Error logs',
	'route:/tools/dict': 'Dictionaries',
	'route:/tools/build': 'Codegen',
	'route:/profile': 'Profile',
	'route:/message': 'Messages'
};

const messages: Record<Locale, Record<string, string>> = { 'zh-CN': zh, 'en-US': en };

class LocaleStore {
	locale = $state<Locale>('zh-CN');

	constructor() {
		if (typeof localStorage !== 'undefined') {
			const saved = localStorage.getItem('s2admin.locale');
			if (saved === 'en-US' || saved === 'zh-CN') this.locale = saved;
		}
		this.apply();
	}

	t(key: string): string {
		return messages[this.locale][key] ?? messages['zh-CN'][key] ?? key;
	}

	set(locale: Locale) {
		this.locale = locale;
		if (typeof localStorage !== 'undefined') localStorage.setItem('s2admin.locale', locale);
		this.apply();
	}

	toggle() {
		this.set(this.locale === 'zh-CN' ? 'en-US' : 'zh-CN');
	}

	private apply() {
		if (typeof document !== 'undefined') document.documentElement.lang = this.locale;
	}
}

export const localeStore = new LocaleStore();
