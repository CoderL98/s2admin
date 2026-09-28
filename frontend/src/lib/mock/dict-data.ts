/**
 * 字典数据 mock 数据 —— 对应 sys_dict_data
 */
import type { DictData } from '$lib/types/entities';
export type { DictData } from '$lib/types/entities';

const now = new Date().toISOString().slice(0, 19).replace('T', ' ');

export const mockDictData: DictData[] = [
	// user_status
	{ id: 1, dictTypeId: 1, dictTypeCode: 'user_status', label: '正常', value: '0', sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 2, dictTypeId: 1, dictTypeCode: 'user_status', label: '禁用', value: '1', sort: 2, status: 0, createBy: 1, createTime: now },
	{ id: 3, dictTypeId: 1, dictTypeCode: 'user_status', label: '锁定', value: '2', sort: 3, status: 0, createBy: 1, createTime: now },
	{ id: 4, dictTypeId: 1, dictTypeCode: 'user_status', label: '过期', value: '3', sort: 4, status: 0, createBy: 1, createTime: now },
	// role_status
	{ id: 5, dictTypeId: 2, dictTypeCode: 'role_status', label: '正常', value: '0', sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 6, dictTypeId: 2, dictTypeCode: 'role_status', label: '停用', value: '1', sort: 2, status: 0, createBy: 1, createTime: now },
	// menu_type
	{ id: 7, dictTypeId: 3, dictTypeCode: 'menu_type', label: '目录', value: '1', sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 8, dictTypeId: 3, dictTypeCode: 'menu_type', label: '菜单', value: '2', sort: 2, status: 0, createBy: 1, createTime: now },
	{ id: 9, dictTypeId: 3, dictTypeCode: 'menu_type', label: '按钮', value: '3', sort: 3, status: 0, createBy: 1, createTime: now },
	// permission_type
	{ id: 10, dictTypeId: 4, dictTypeCode: 'permission_type', label: '菜单权限', value: '1', sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 11, dictTypeId: 4, dictTypeCode: 'permission_type', label: '按钮权限', value: '2', sort: 2, status: 0, createBy: 1, createTime: now },
	{ id: 12, dictTypeId: 4, dictTypeCode: 'permission_type', label: 'API 权限', value: '3', sort: 3, status: 0, createBy: 1, createTime: now },
	// login_status
	{ id: 13, dictTypeId: 5, dictTypeCode: 'login_status', label: '成功', value: '0', sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 14, dictTypeId: 5, dictTypeCode: 'login_status', label: '失败', value: '1', sort: 2, status: 0, createBy: 1, createTime: now },
	// operation_type
	{ id: 15, dictTypeId: 6, dictTypeCode: 'operation_type', label: '新增', value: 'ADD', sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 16, dictTypeId: 6, dictTypeCode: 'operation_type', label: '修改', value: 'UPDATE', sort: 2, status: 0, createBy: 1, createTime: now },
	{ id: 17, dictTypeId: 6, dictTypeCode: 'operation_type', label: '删除', value: 'DELETE', sort: 3, status: 0, createBy: 1, createTime: now },
	{ id: 18, dictTypeId: 6, dictTypeCode: 'operation_type', label: '查询', value: 'QUERY', sort: 4, status: 0, createBy: 1, createTime: now },
	{ id: 19, dictTypeId: 6, dictTypeCode: 'operation_type', label: '导入', value: 'IMPORT', sort: 5, status: 0, createBy: 1, createTime: now },
	{ id: 20, dictTypeId: 6, dictTypeCode: 'operation_type', label: '导出', value: 'EXPORT', sort: 6, status: 0, createBy: 1, createTime: now },
	// data_scope
	{ id: 21, dictTypeId: 7, dictTypeCode: 'data_scope', label: '全部数据', value: '1', sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 22, dictTypeId: 7, dictTypeCode: 'data_scope', label: '本部门及以下', value: '2', sort: 2, status: 0, createBy: 1, createTime: now },
	{ id: 23, dictTypeId: 7, dictTypeCode: 'data_scope', label: '本部门', value: '3', sort: 3, status: 0, createBy: 1, createTime: now },
	{ id: 24, dictTypeId: 7, dictTypeCode: 'data_scope', label: '本人', value: '4', sort: 4, status: 0, createBy: 1, createTime: now },
	// sys_switch
	{ id: 25, dictTypeId: 8, dictTypeCode: 'sys_switch', label: '开启', value: 'true', sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 26, dictTypeId: 8, dictTypeCode: 'sys_switch', label: '关闭', value: 'false', sort: 2, status: 0, createBy: 1, createTime: now },
	// gender
	{ id: 27, dictTypeId: 9, dictTypeCode: 'gender', label: '男', value: '1', sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 28, dictTypeId: 9, dictTypeCode: 'gender', label: '女', value: '2', sort: 2, status: 0, createBy: 1, createTime: now },
	{ id: 29, dictTypeId: 9, dictTypeCode: 'gender', label: '未知', value: '0', sort: 3, status: 0, createBy: 1, createTime: now },
	// education
	{ id: 30, dictTypeId: 10, dictTypeCode: 'education', label: '高中', value: '1', sort: 1, status: 0, createBy: 1, createTime: now },
	{ id: 31, dictTypeId: 10, dictTypeCode: 'education', label: '大专', value: '2', sort: 2, status: 0, createBy: 1, createTime: now },
	{ id: 32, dictTypeId: 10, dictTypeCode: 'education', label: '本科', value: '3', sort: 3, status: 0, createBy: 1, createTime: now },
	{ id: 33, dictTypeId: 10, dictTypeCode: 'education', label: '硕士', value: '4', sort: 4, status: 0, createBy: 1, createTime: now },
	{ id: 34, dictTypeId: 10, dictTypeCode: 'education', label: '博士', value: '5', sort: 5, status: 0, createBy: 1, createTime: now }
];
