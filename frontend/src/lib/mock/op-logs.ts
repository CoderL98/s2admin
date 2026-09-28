/**
 * 操作日志 mock 数据 —— 对应 sys_operation_log
 */
export interface OperationLog {
	id: number;
	userId: number;
	username: string;
	operation: string; // 新增 / 修改 / 删除 / 查询 / 导入 / 导出
	module: string; // 用户管理 / 角色管理 / ...
	method: string; // UserController.add / com.s2admin...
	url: string;
	ip: string;
	location: string;
	status: 0 | 1; // 0 成功 1 失败
	errorMsg?: string;
	executeTime: number; // ms
	operationTime: string;
}

function fmt(d: Date): string {
	return d.toISOString().slice(0, 19).replace('T', ' ');
}

const base = new Date('2026-06-26T08:00:00');
function ago(minutes: number): string {
	return fmt(new Date(base.getTime() - minutes * 60_000));
}

export const mockOpLogs: OperationLog[] = [
	{ id: 1, userId: 1, username: 'admin', operation: '新增', module: '用户管理', method: 'UserController.add', url: '/api/system/user', ip: '127.0.0.1', location: '本机地址', status: 0, executeTime: 45, operationTime: ago(10) },
	{ id: 2, userId: 1, username: 'admin', operation: '修改', module: '用户管理', method: 'UserController.update', url: '/api/system/user/3', ip: '127.0.0.1', location: '本机地址', status: 0, executeTime: 32, operationTime: ago(20) },
	{ id: 3, userId: 2, username: 'manager', operation: '查询', module: '角色管理', method: 'RoleController.list', url: '/api/system/role?pageNum=1&pageSize=10', ip: '192.168.1.21', location: '局域网', status: 0, executeTime: 18, operationTime: ago(35) },
	{ id: 4, userId: 1, username: 'admin', operation: '删除', module: '菜单管理', method: 'MenuController.remove', url: '/api/system/menu/32', ip: '127.0.0.1', location: '本机地址', status: 1, errorMsg: '子菜单不为空,不允许删除', executeTime: 12, operationTime: ago(50) },
	{ id: 5, userId: 1, username: 'admin', operation: '授权', module: '角色管理', method: 'RoleController.assignPermissions', url: '/api/system/role/2/permissions', ip: '127.0.0.1', location: '本机地址', status: 0, executeTime: 56, operationTime: ago(70) },
	{ id: 6, userId: 3, username: 'developer', operation: '导出', module: '用户管理', method: 'UserController.export', url: '/api/system/user/export', ip: '192.168.1.32', location: '局域网', status: 0, executeTime: 1240, operationTime: ago(90) },
	{ id: 7, userId: 1, username: 'admin', operation: '修改', module: '系统配置', method: 'SysConfigController.update', url: '/api/system/config/2', ip: '127.0.0.1', location: '本机地址', status: 0, executeTime: 28, operationTime: ago(120) },
	{ id: 8, userId: 5, username: 'tester', operation: '查询', module: '登录日志', method: 'LogController.loginLogPage', url: '/api/monitor/login-log', ip: '192.168.1.40', location: '局域网', status: 0, executeTime: 15, operationTime: ago(180) },
	{ id: 9, userId: 1, username: 'admin', operation: '新增', module: '字典管理', method: 'DictController.addType', url: '/api/system/dict/type', ip: '127.0.0.1', location: '本机地址', status: 0, executeTime: 22, operationTime: ago(220) },
	{ id: 10, userId: 2, username: 'manager', operation: '登录', module: '认证', method: 'AuthController.login', url: '/api/auth/login', ip: '192.168.1.21', location: '局域网', status: 0, executeTime: 134, operationTime: ago(15) },
	{ id: 11, userId: 4, username: 'developer2', operation: '导入', module: '用户管理', method: 'UserController.import', url: '/api/system/user/import', ip: '192.168.1.35', location: '局域网', status: 1, errorMsg: '第3行手机号格式错误', executeTime: 870, operationTime: ago(260) },
	{ id: 12, userId: 1, username: 'admin', operation: '删除', module: '操作日志', method: 'LogController.cleanOpLog', url: '/api/monitor/op-log/clean', ip: '127.0.0.1', location: '本机地址', status: 0, executeTime: 2300, operationTime: ago(300) }
];
