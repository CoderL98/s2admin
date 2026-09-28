/**
 * 权限 mock 数据 —— 对应 sys_permission
 */
import type { Permission } from '$lib/types/entities';
export type { Permission } from '$lib/types/entities';

const now = new Date().toISOString().slice(0, 19).replace('T', ' ');

export const mockPermissions: Permission[] = [
	{ id: 1, name: '系统管理', code: 'system', type: 1, parentId: 0, sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 2, name: '用户查看', code: 'system:user:view', type: 2, parentId: 1, sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 3, name: '用户新增', code: 'system:user:add', type: 2, parentId: 1, sort: 2, status: 0, createBy: 1, createTime: now },
	{ id: 4, name: '用户编辑', code: 'system:user:edit', type: 2, parentId: 1, sort: 3, status: 0, createBy: 1, createTime: now },
	{ id: 5, name: '用户删除', code: 'system:user:delete', type: 2, parentId: 1, sort: 4, status: 0, createBy: 1, createTime: now },
	{ id: 6, name: '用户导出', code: 'system:user:export', type: 2, parentId: 1, sort: 5, status: 0, createBy: 1, createTime: now },
	{ id: 7, name: '角色查看', code: 'system:role:view', type: 2, parentId: 1, sort: 11, status: 0, createBy: 1, createTime: now },
	{ id: 8, name: '角色新增', code: 'system:role:add', type: 2, parentId: 1, sort: 12, status: 0, createBy: 1, createTime: now },
	{ id: 9, name: '角色编辑', code: 'system:role:edit', type: 2, parentId: 1, sort: 13, status: 0, createBy: 1, createTime: now },
	{ id: 10, name: '角色删除', code: 'system:role:delete', type: 2, parentId: 1, sort: 14, status: 0, createBy: 1, createTime: now },
	{ id: 11, name: '角色授权', code: 'system:role:assign', type: 2, parentId: 1, sort: 15, status: 0, createBy: 1, createTime: now },
	{ id: 12, name: '菜单查看', code: 'system:menu:view', type: 2, parentId: 1, sort: 21, status: 0, createBy: 1, createTime: now },
	{ id: 13, name: '菜单新增', code: 'system:menu:add', type: 2, parentId: 1, sort: 22, status: 0, createBy: 1, createTime: now },
	{ id: 14, name: '菜单编辑', code: 'system:menu:edit', type: 2, parentId: 1, sort: 23, status: 0, createBy: 1, createTime: now },
	{ id: 15, name: '菜单删除', code: 'system:menu:delete', type: 2, parentId: 1, sort: 24, status: 0, createBy: 1, createTime: now },
	{ id: 16, name: '监控中心', code: 'monitor', type: 1, parentId: 0, sort: 2, status: 0, createBy: 1, createTime: now },
	{ id: 17, name: '日志查看', code: 'monitor:log:view', type: 2, parentId: 16, sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 18, name: '日志导出', code: 'monitor:log:export', type: 2, parentId: 16, sort: 2, status: 0, createBy: 1, createTime: now },
	{ id: 19, name: '工具中心', code: 'tools', type: 1, parentId: 0, sort: 3, status: 0, createBy: 1, createTime: now },
	{ id: 20, name: '字典查看', code: 'tools:dict:view', type: 2, parentId: 19, sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 21, name: '字典编辑', code: 'tools:dict:edit', type: 2, parentId: 19, sort: 2, status: 0, createBy: 1, createTime: now }
];
