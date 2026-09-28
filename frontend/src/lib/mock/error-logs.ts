/**
 * 异常日志 mock 数据 —— 对应 sys_error_log
 */
export interface ErrorLog {
	id: number;
	traceId: string;
	userId?: number;
	username?: string;
	ip: string;
	url: string;
	method: string;
	params?: string;
	exception: string;
	stackTrace: string;
	errorTime: string;
}

function fmt(d: Date): string {
	return d.toISOString().slice(0, 19).replace('T', ' ');
}

const base = new Date('2026-06-26T08:00:00');
function ago(minutes: number): string {
	return fmt(new Date(base.getTime() - minutes * 60_000));
}

export const mockErrorLogs: ErrorLog[] = [
	{
		id: 1,
		traceId: 'a1b2c3d4e5f6',
		userId: 1,
		username: 'admin',
		ip: '127.0.0.1',
		url: '/api/system/user/9999',
		method: 'UserController.getById',
		params: '{"id":9999}',
		exception: 'com.s2admin.common.exception.BusinessException: 用户不存在',
		stackTrace: 'at UserController.lambda$getById$0(UserController.java:45)\nat jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\nat org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:205)',
		errorTime: ago(30)
	},
	{
		id: 2,
		traceId: 'b2c3d4e5f6a7',
		userId: 1,
		username: 'admin',
		ip: '127.0.0.1',
		url: '/api/auth/login',
		method: 'AuthController.login',
		params: '{"username":"admin","password":"***"}',
		exception: 'org.springframework.security.authentication.BadCredentialsException: 用户名或密码错误',
		stackTrace: 'at AuthService.login(AuthService.java:78)\nat AuthController.lambda$login$0(AuthController.java:35)',
		errorTime: ago(90)
	},
	{
		id: 3,
		traceId: 'c3d4e5f6a7b8',
		ip: '192.168.1.32',
		url: '/api/system/role',
		method: 'RoleController.list',
		params: '{"pageNum":1,"pageSize":10}',
		exception: 'org.springframework.dao.DataIntegrityViolationException: Duplicate entry for key \'code\'',
		stackTrace: 'at RoleService.create(RoleService.java:62)\nat RoleController.lambda$create$1(RoleController.java:48)',
		errorTime: ago(150)
	},
	{
		id: 4,
		traceId: 'd4e5f6a7b8c9',
		userId: 4,
		username: 'developer2',
		ip: '192.168.1.35',
		url: '/api/system/user/import',
		method: 'UserController.import',
		params: '{"file":"users.xlsx"}',
		exception: 'java.lang.IllegalArgumentException: 第3行手机号格式错误:1380013800X',
		stackTrace: 'at ExcelImportListener.invoke(ExcelImportListener.java:42)\nat UserService.lambda$import$0(UserService.java:108)',
		errorTime: ago(260)
	},
	{
		id: 5,
		traceId: 'e5f6a7b8c9d0',
		ip: '203.0.113.99',
		url: '/api/system/menu',
		method: 'MenuController.list',
		params: '{}',
		exception: 'org.springframework.dao.QueryTimeoutException: Query timed out after 30s',
		stackTrace: 'at jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\nat Hibernate.query.doQuery(Hibernate.java:2310)',
		errorTime: ago(420)
	},
	{
		id: 6,
		traceId: 'f6a7b8c9d0e1',
		userId: 1,
		username: 'admin',
		ip: '127.0.0.1',
		url: '/api/system/config/1',
		method: 'SysConfigController.update',
		params: '{"configValue":"abc"}',
		exception: 'jakarta.validation.ConstraintViolationException: update.update.configValue: 配置值格式不合法',
		stackTrace: 'at SysConfigController.lambda$update$0(SysConfigController.java:55)',
		errorTime: ago(600)
	},
	{
		id: 7,
		traceId: 'a7b8c9d0e1f2',
		ip: '198.51.100.55',
		url: '/api/auth/refresh',
		method: 'AuthController.refresh',
		params: '{}',
		exception: 'io.jsonwebtoken.ExpiredJwtException: JWT expired at 2026-06-25T10:00:00Z',
		stackTrace: 'at JwtTokenProvider.parseClaims(JwtTokenProvider.java:88)\nat AuthService.refresh(AuthService.java:124)',
		errorTime: ago(720)
	},
	{
		id: 8,
		traceId: 'b8c9d0e1f2a3',
		userId: 7,
		username: 'product',
		ip: '10.0.0.5',
		url: '/api/system/file/upload',
		method: 'FileController.upload',
		params: '{"file":"report.pdf","size":15728640}',
		exception: 'com.s2admin.common.exception.BusinessException: 文件超过最大限制 10MB',
		stackTrace: 'at FileService.upload(FileService.java:42)',
		errorTime: ago(900)
	}
];
