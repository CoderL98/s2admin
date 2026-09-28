import type { BaseEntity } from './auth';

export interface User extends BaseEntity {
	username: string;
	nickname: string;
	email: string;
	phone: string;
	avatar?: string;
	status: 0 | 1 | 2 | 3;
	deptId?: number;
	deptName?: string;
	roleIds?: number[];
	roleNames?: string[];
	pwdReset?: number;
	province?: string;
	city?: string;
	district?: string;
}

export interface Role extends BaseEntity {
	name: string;
	code: string;
	sort: number;
	dataScope: 1 | 2 | 3 | 4;
	status: 0 | 1;
	userCount?: number;
}

export interface Menu extends BaseEntity {
	name: string;
	parentId: number;
	path: string;
	component?: string;
	redirect?: string;
	permission?: string;
	icon?: string;
	sort: number;
	type: 1 | 2 | 3;
	hidden: 0 | 1;
	status: 0 | 1;
	children?: Menu[];
}

export interface Permission extends BaseEntity {
	name: string;
	code: string;
	type: 1 | 2 | 3;
	parentId: number;
	path?: string;
	icon?: string;
	sort: number;
	status: 0 | 1;
	roleCount?: number;
	children?: Permission[];
}

export interface FlowNode {
	id?: number;
	name: string;
	sort?: number;
	roleId?: number;
	roleName?: string;
	nodeType?: number;
	signMode?: number;
	rejectTo?: number | null;
	conditionExpr?: string;
	yesSeq?: number | null;
	noSeq?: number | null;
}

export interface Tenant {
	id: number;
	name: string;
	code: string;
	status: 0 | 1;
	contact?: string;
	remark?: string;
	userCount?: number;
	createTime?: string;
}

export interface FlowDef extends BaseEntity {
	name: string;
	code: string;
	status: 0 | 1;
	nodeCount?: number;
	nodes?: FlowNode[];
}

export interface ApprovalRecord {
	id: number;
	seq?: number;
	nodeName?: string;
	action: string;
	operatorName?: string;
	comment?: string;
	createTime?: string;
}

export interface ApprovalItem extends BaseEntity {
	flowId: number;
	flowName?: string;
	title: string;
	content?: string;
	applicantId: number;
	applicantName?: string;
	status: 1 | 2 | 3 | 4;
	currentSeq?: number;
	currentRoleId?: number;
	currentNodeName?: string;
	currentRoleName?: string;
	canHandle?: boolean;
	records?: ApprovalRecord[];
}

export interface SysConfig extends BaseEntity {
	configKey: string;
	configValue: string;
	configType: 'string' | 'number' | 'boolean';
	groupCode: string;
}

export interface DictType extends BaseEntity {
	name: string;
	code: string;
	status: 0 | 1;
}

export interface DictData extends BaseEntity {
	dictTypeId: number;
	dictTypeCode?: string;
	label: string;
	value: string;
	sort: number;
	status: 0 | 1;
}

export interface LoginLog {
	id: number;
	userId: number;
	username: string;
	ip: string;
	location: string;
	browser: string;
	os: string;
	status: 0 | 1;
	message: string;
	loginTime: string;
}

export interface OperationLog {
	id: number;
	userId: number;
	username: string;
	operation: string;
	module: string;
	method: string;
	url: string;
	ip: string;
	location: string;
	status: 0 | 1;
	errorMsg?: string;
	oldValue?: string;
	newValue?: string;
	executeTime: number;
	operationTime: string;
}

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

export interface DashboardStats {
	userCount: number;
	roleCount: number;
	todayLoginCount: number;
	errorCount: number;
}

export interface FileVO {
	url: string;
	name: string;
	size: number;
}

export interface ImportResult {
	created: number;
	skipped: number;
	errors: string[];
}

export const DATA_SCOPE_OPTIONS = [
	{ value: 1, label: '全部数据' },
	{ value: 2, label: '本部门及以下' },
	{ value: 3, label: '本部门' },
	{ value: 4, label: '本人' }
] as const;

export const MENU_TYPE_OPTIONS = [
	{ value: 1, label: '目录' },
	{ value: 2, label: '菜单' },
	{ value: 3, label: '按钮' }
] as const;

export const PERMISSION_TYPE_OPTIONS = [
	{ value: 1, label: '菜单' },
	{ value: 2, label: '按钮' },
	{ value: 3, label: 'API' }
] as const;

export interface Dept extends BaseEntity {
	name: string;
	parentId: number;
	ancestors?: string;
	sort: number;
	leader?: string;
	phone?: string;
	email?: string;
	status: 0 | 1;
	children?: Dept[];
}

export interface StoredFile extends BaseEntity {
	url: string;
	name: string;
	storedName?: string;
	size: number;
	category?: string;
	contentType?: string;
	storageType?: string;
}

export interface Notice extends BaseEntity {
	title: string;
	content?: string;
	type: 1 | 2;
	status: 0 | 1;
	pinned: 0 | 1;
	publishTime?: string;
}

export interface InboxMessage extends BaseEntity {
	title: string;
	content?: string;
	senderId?: number;
	senderName?: string;
	receiverId: number;
	readFlag: 0 | 1;
	readTime?: string;
}
