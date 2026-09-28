/**
 * 登录日志 mock 数据 —— 对应 sys_login_log
 */
export interface LoginLog {
	id: number;
	userId: number;
	username: string;
	ip: string;
	location: string;
	browser: string;
	os: string;
	status: 0 | 1; // 0 成功 1 失败
	message: string;
	loginTime: string;
}

function fmt(d: Date): string {
	return d.toISOString().slice(0, 19).replace('T', ' ');
}

const base = new Date('2026-06-26T08:00:00');
function ago(minutes: number): string {
	return fmt(new Date(base.getTime() - minutes * 60_000));
}

export const mockLoginLogs: LoginLog[] = [
	{ id: 1, userId: 1, username: 'admin', ip: '127.0.0.1', location: '本机地址', browser: 'Chrome 138', os: 'Windows 11', status: 0, message: '登录成功', loginTime: ago(5) },
	{ id: 2, userId: 2, username: 'manager', ip: '192.168.1.21', location: '局域网', browser: 'Edge 137', os: 'macOS 15', status: 0, message: '登录成功', loginTime: ago(15) },
	{ id: 3, userId: 1, username: 'admin', ip: '192.168.1.10', location: '局域网', browser: 'Chrome 138', os: 'macOS 15', status: 0, message: '登录成功', loginTime: ago(45) },
	{ id: 4, userId: 3, username: 'developer', ip: '192.168.1.32', location: '局域网', browser: 'Firefox 130', os: 'Ubuntu 24.04', status: 1, message: '密码错误', loginTime: ago(75) },
	{ id: 5, userId: 5, username: 'tester', ip: '192.168.1.40', location: '局域网', browser: 'Chrome 137', os: 'Windows 10', status: 0, message: '登录成功', loginTime: ago(120) },
	{ id: 6, userId: 7, username: 'product', ip: '10.0.0.5', location: '局域网', browser: 'Safari 18', os: 'macOS 15', status: 0, message: '登录成功', loginTime: ago(180) },
	{ id: 7, userId: 15, username: 'locked', ip: '203.0.113.5', location: '外网', browser: 'Chrome 138', os: 'Windows 11', status: 1, message: '账号已锁定', loginTime: ago(240) },
	{ id: 8, userId: 1, username: 'admin', ip: '127.0.0.1', location: '本机地址', browser: 'Chrome 138', os: 'Linux', status: 0, message: '登录成功', loginTime: ago(1440) },
	{ id: 9, userId: 2, username: 'manager', ip: '192.168.1.21', location: '局域网', browser: 'Edge 137', os: 'macOS 15', status: 0, message: '登录成功', loginTime: ago(1500) },
	{ id: 10, userId: 4, username: 'developer2', ip: '192.168.1.35', location: '局域网', browser: 'Chrome 138', os: 'Windows 11', status: 0, message: '登录成功', loginTime: ago(1800) },
	{ id: 11, userId: 1, username: 'admin', ip: '198.51.100.7', location: '外网', browser: 'Chrome 138', os: 'Windows 11', status: 1, message: '验证码错误', loginTime: ago(2000) },
	{ id: 12, userId: 8, username: 'operation', ip: '192.168.1.50', location: '局域网', browser: 'Chrome 137', os: 'Windows 10', status: 0, message: '登录成功', loginTime: ago(2200) },
	{ id: 13, userId: 11, username: 'sales01', ip: '192.168.1.61', location: '局域网', browser: 'Edge 137', os: 'Windows 11', status: 0, message: '登录成功', loginTime: ago(2400) },
	{ id: 14, userId: 9, username: 'finance', ip: '192.168.1.71', location: '局域网', browser: 'Chrome 138', os: 'Windows 11', status: 0, message: '登录成功', loginTime: ago(2600) },
	{ id: 15, userId: 10, username: 'hr', ip: '192.168.1.81', location: '局域网', browser: 'Safari 18', os: 'macOS 15', status: 0, message: '登录成功', loginTime: ago(2880) }
];
