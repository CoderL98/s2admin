/**
 * 字典类型 mock 数据 —— 对应 sys_dict_type
 */
import type { DictType } from '$lib/types/entities';
export type { DictType } from '$lib/types/entities';

const now = new Date().toISOString().slice(0, 19).replace('T', ' ');

export const mockDictTypes: DictType[] = [
	{ id: 1, name: '用户状态', code: 'user_status', status: 0, remark: '用户账号状态', createBy: 1, createTime: now },
	{ id: 2, name: '角色状态', code: 'role_status', status: 0, remark: '角色启用状态', createBy: 1, createTime: now },
	{ id: 3, name: '菜单类型', code: 'menu_type', status: 0, remark: '菜单节点类型', createBy: 1, createTime: now },
	{ id: 4, name: '权限类型', code: 'permission_type', status: 0, remark: '权限节点类型', createBy: 1, createTime: now },
	{ id: 5, name: '登录状态', code: 'login_status', status: 0, remark: '登录成功/失败', createBy: 1, createTime: now },
	{ id: 6, name: '操作类型', code: 'operation_type', status: 0, remark: '操作日志类型', createBy: 1, createTime: now },
	{ id: 7, name: '数据范围', code: 'data_scope', status: 0, remark: '角色数据范围', createBy: 1, createTime: now },
	{ id: 8, name: '系统开关', code: 'sys_switch', status: 0, remark: '系统功能开关', createBy: 1, createTime: now },
	{ id: 9, name: '性别', code: 'gender', status: 0, remark: '用户性别', createBy: 1, createTime: now },
	{ id: 10, name: '学历', code: 'education', status: 0, remark: '学历层次', createBy: 1, createTime: now }
];
