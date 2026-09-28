/**
 * 用户 mock 数据 —— 对应 sys_user
 * 字段与 init.sql 一致
 */
import type { User } from '$lib/types/entities';
export type { User } from '$lib/types/entities';

const now = new Date().toISOString().slice(0, 19).replace('T', ' ');

export const mockUsers: User[] = [
	{ id: 1, username: 'admin', nickname: '超级管理员', email: 'admin@example.com', phone: '13800138000', status: 0, deptId: 1, deptName: '总经办', createBy: 1, createTime: now },
	{ id: 2, username: 'manager', nickname: '张经理', email: 'manager@example.com', phone: '13800138001', status: 0, deptId: 2, deptName: '研发部', createBy: 1, createTime: now },
	{ id: 3, username: 'developer', nickname: '李开发', email: 'dev1@example.com', phone: '13800138002', status: 0, deptId: 2, deptName: '研发部', createBy: 1, createTime: now },
	{ id: 4, username: 'developer2', nickname: '王开发', email: 'dev2@example.com', phone: '13800138003', status: 0, deptId: 2, deptName: '研发部', createBy: 1, createTime: now },
	{ id: 5, username: 'tester', nickname: '赵测试', email: 'qa@example.com', phone: '13800138004', status: 0, deptId: 3, deptName: '测试部', createBy: 1, createTime: now },
	{ id: 6, username: 'designer', nickname: '钱设计', email: 'design@example.com', phone: '13800138005', status: 0, deptId: 4, deptName: '设计部', createBy: 1, createTime: now },
	{ id: 7, username: 'product', nickname: '孙产品', email: 'pm@example.com', phone: '13800138006', status: 0, deptId: 5, deptName: '产品部', createBy: 1, createTime: now },
	{ id: 8, username: 'operation', nickname: '周运营', email: 'op@example.com', phone: '13800138007', status: 0, deptId: 6, deptName: '运营部', createBy: 1, createTime: now },
	{ id: 9, username: 'finance', nickname: '吴财务', email: 'fin@example.com', phone: '13800138008', status: 0, deptId: 7, deptName: '财务部', createBy: 1, createTime: now },
	{ id: 10, username: 'hr', nickname: '郑人事', email: 'hr@example.com', phone: '13800138009', status: 0, deptId: 8, deptName: '人事部', createBy: 1, createTime: now },
	{ id: 11, username: 'sales01', nickname: '冯销售一', email: 'sales1@example.com', phone: '13800138010', status: 0, deptId: 9, deptName: '销售部', createBy: 1, createTime: now },
	{ id: 12, username: 'sales02', nickname: '陈销售二', email: 'sales2@example.com', phone: '13800138011', status: 0, deptId: 9, deptName: '销售部', createBy: 1, createTime: now },
	{ id: 13, username: 'sales03', nickname: '褚销售三', email: 'sales3@example.com', phone: '13800138012', status: 1, deptId: 9, deptName: '销售部', createBy: 1, createTime: now, remark: '试用期未通过' },
	{ id: 14, username: 'intern', nickname: '卫实习生', email: 'intern@example.com', phone: '13800138013', status: 0, deptId: 2, deptName: '研发部', createBy: 1, createTime: now },
	{ id: 15, username: 'locked', nickname: '蒋锁定', email: 'locked@example.com', phone: '13800138014', status: 2, deptId: 2, deptName: '研发部', createBy: 1, createTime: now, remark: '密码连续错误锁定' },
	{ id: 16, username: 'expired', nickname: '沈过期', email: 'expired@example.com', phone: '13800138015', status: 3, deptId: 3, deptName: '测试部', createBy: 1, createTime: now, remark: '账号到期' },
	{ id: 17, username: 'audit', nickname: '韩审计', email: 'audit@example.com', phone: '13800138016', status: 0, deptId: 10, deptName: '审计部', createBy: 1, createTime: now },
	{ id: 18, username: 'guest', nickname: '杨访客', email: 'guest@example.com', phone: '13800138017', status: 0, deptId: 8, deptName: '人事部', createBy: 1, createTime: now },
	{ id: 19, username: 'readonly', nickname: '朱只读', email: 'readonly@example.com', phone: '13800138018', status: 0, deptId: 4, deptName: '设计部', createBy: 1, createTime: now },
	{ id: 20, username: 'temp', nickname: '秦临时', email: 'temp@example.com', phone: '13800138019', status: 0, deptId: 5, deptName: '产品部', createBy: 1, createTime: now }
];
