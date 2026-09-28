/**
 * 通用分页响应结构
 * 与后端 PageResult<T> 对应
 */
export interface PageResult<T> {
	records: T[];
	total: number;
	size: number;
	current: number;
	pages: number;
}

/**
 * 通用分页查询参数
 */
export interface PageQuery {
	pageNum: number;
	pageSize: number;
	keyword?: string;
	[key: string]: unknown;
}

/**
 * 登录用户信息
 */
export interface LoginUser {
	id: number;
	username: string;
	nickname: string;
	email: string;
	phone?: string;
	avatar?: string;
	province?: string;
	city?: string;
	district?: string;
	roles: string[];
	permissions: string[];
	mustChangePassword?: boolean;
	tenantId?: number | null;
}

/**
 * 登录表单
 */
export interface LoginForm {
	username: string;
	password: string;
	captcha?: string;
	captchaKey?: string;
	rememberMe?: boolean;
}

export interface CaptchaResult {
	enabled: boolean;
	captchaKey?: string;
	image?: string;
}

export interface ProfilePayload {
	nickname: string;
	email?: string;
	phone?: string;
	avatar?: string;
	province?: string;
	city?: string;
	district?: string;
}

export interface LoginSession {
	sid: string;
	iat: number;
	ip: string;
	ua: string;
	current: boolean;
}

export interface ChangePasswordPayload {
	oldPassword: string;
	newPassword: string;
}

/**
 * 登录响应
 */
export interface LoginResult {
	token: string;
	refreshToken: string;
	expiresIn: number;
	user: LoginUser;
}

/**
 * 业务实体通用字段(对应后端 BaseEntity)
 */
export interface BaseEntity {
	id: number;
	createBy?: number;
	createTime?: string;
	updateBy?: number;
	updateTime?: string;
	remark?: string;
}
