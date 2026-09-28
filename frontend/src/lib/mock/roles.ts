/**
 * 角色 mock 数据 —— 对应 sys_role
 */
import type { Role } from '$lib/types/entities';
export type { Role } from '$lib/types/entities';

const now = new Date().toISOString().slice(0, 19).replace('T', ' ');

export const mockRoles: Role[] = [
	{ id: 1, name: '超级管理员', code: 'SUPER_ADMIN', sort: 1, dataScope: 1, status: 0, remark: '系统最高权限', createBy: 1, createTime: now },
	{ id: 2, name: '系统管理员', code: 'ADMIN', sort: 2, dataScope: 1, status: 0, remark: '系统管理功能', createBy: 1, createTime: now },
	{ id: 3, name: '普通用户', code: 'USER', sort: 3, dataScope: 4, status: 0, remark: '基本功能', createBy: 1, createTime: now },
	{ id: 4, name: '审计员', code: 'AUDITOR', sort: 4, dataScope: 1, status: 0, remark: '仅查看日志', createBy: 1, createTime: now },
	{ id: 5, name: '访客', code: 'GUEST', sort: 5, dataScope: 4, status: 0, remark: '只读权限', createBy: 1, createTime: now },
	{ id: 6, name: '研发主管', code: 'RD_LEAD', sort: 10, dataScope: 2, status: 0, remark: '研发部主管', createBy: 1, createTime: now },
	{ id: 7, name: '测试主管', code: 'QA_LEAD', sort: 11, dataScope: 3, status: 0, remark: '测试部主管', createBy: 1, createTime: now },
	{ id: 8, name: '销售主管', code: 'SALES_LEAD', sort: 12, dataScope: 2, status: 0, remark: '销售部主管', createBy: 1, createTime: now },
	{ id: 9, name: '运营专员', code: 'OPS', sort: 20, dataScope: 4, status: 0, remark: '运营人员', createBy: 1, createTime: now },
	{ id: 10, name: '财务专员', code: 'FIN', sort: 21, dataScope: 4, status: 0, remark: '财务人员', createBy: 1, createTime: now },
	{ id: 11, name: '人事专员', code: 'HR', sort: 22, dataScope: 4, status: 0, remark: '人事人员', createBy: 1, createTime: now },
	{ id: 12, name: '设计主管', code: 'DESIGN_LEAD', sort: 13, dataScope: 2, status: 0, remark: '设计部主管', createBy: 1, createTime: now },
	{ id: 13, name: '产品经理', code: 'PM', sort: 14, dataScope: 4, status: 0, remark: '产品经理', createBy: 1, createTime: now },
	{ id: 14, name: '已停用角色', code: 'DEPRECATED', sort: 99, dataScope: 1, status: 1, remark: '已停用,历史保留', createBy: 1, createTime: now }
];
