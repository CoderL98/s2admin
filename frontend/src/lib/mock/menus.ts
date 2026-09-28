/**
 * 菜单 mock 数据 —— 对应 sys_menu
 */
import type { Menu } from '$lib/types/entities';
export type { Menu } from '$lib/types/entities';

const now = new Date().toISOString().slice(0, 19).replace('T', ' ');

export const mockMenus: Menu[] = [
	// 顶级目录
	{ id: 1, name: '系统管理', parentId: 0, path: '/system', component: 'Layout', icon: 'Setting', sort: 1, type: 1, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 2, name: '系统监控', parentId: 0, path: '/monitor', component: 'Layout', icon: 'Monitor', sort: 2, type: 1, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 3, name: '系统工具', parentId: 0, path: '/tools', component: 'Layout', icon: 'Wrench', sort: 3, type: 1, hidden: 0, status: 0, createBy: 1, createTime: now },
	// 系统管理子菜单
	{ id: 11, name: '用户管理', parentId: 1, path: '/system/user', component: '/system/user/index', icon: 'User', permission: 'system:user:view', sort: 1, type: 2, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 12, name: '角色管理', parentId: 1, path: '/system/role', component: '/system/role/index', icon: 'Role', permission: 'system:role:view', sort: 2, type: 2, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 13, name: '菜单管理', parentId: 1, path: '/system/menu', component: '/system/menu/index', icon: 'Menu', permission: 'system:menu:view', sort: 3, type: 2, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 14, name: '权限管理', parentId: 1, path: '/system/permission', component: '/system/permission/index', icon: 'Shield', permission: 'system:permission:view', sort: 4, type: 2, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 15, name: '系统配置', parentId: 1, path: '/system/config', component: '/system/config/index', icon: 'Tool', permission: 'system:config:view', sort: 5, type: 2, hidden: 0, status: 0, createBy: 1, createTime: now },
	// 系统监控子菜单
	{ id: 21, name: '登录日志', parentId: 2, path: '/monitor/login-log', component: '/monitor/login-log/index', icon: 'Log', sort: 1, type: 2, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 22, name: '操作日志', parentId: 2, path: '/monitor/op-log', component: '/monitor/op-log/index', icon: 'FileText', sort: 2, type: 2, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 23, name: '异常日志', parentId: 2, path: '/monitor/error-log', component: '/monitor/error-log/index', icon: 'AlertTriangle', sort: 3, type: 2, hidden: 0, status: 0, createBy: 1, createTime: now },
	// 系统工具子菜单
	{ id: 31, name: '字典管理', parentId: 3, path: '/tools/dict', component: '/tools/dict/index', icon: 'Book', sort: 1, type: 2, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 32, name: '代码生成', parentId: 3, path: '/tools/build', component: '/tools/build/index', icon: 'Code', sort: 2, type: 2, hidden: 0, status: 0, createBy: 1, createTime: now },
	// 用户管理按钮
	{ id: 111, name: '用户新增', parentId: 11, path: '', permission: 'system:user:add', sort: 1, type: 3, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 112, name: '用户编辑', parentId: 11, path: '', permission: 'system:user:edit', sort: 2, type: 3, hidden: 0, status: 0, createBy: 1, createTime: now },
	{ id: 113, name: '用户删除', parentId: 11, path: '', permission: 'system:user:delete', sort: 3, type: 3, hidden: 0, status: 0, createBy: 1, createTime: now }
];
