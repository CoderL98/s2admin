/**
 * 角色管理 API —— mock 实现
 */
import { delay, paginate, nextId } from '$lib/mock/_helpers';
import { mockRoles, type Role } from '$lib/mock/roles';
import type { PageQuery, PageResult } from '$lib/types/auth';

const store: Role[] = [...mockRoles];

export interface RoleQuery extends PageQuery {
	status?: number;
}

export async function getRoleList(q: RoleQuery): Promise<PageResult<Role>> {
	await delay(200);
	let rows = store;
	if (q.keyword) {
		const kw = q.keyword.toLowerCase();
		rows = rows.filter((r) => r.name.toLowerCase().includes(kw) || r.code.toLowerCase().includes(kw));
	}
	if (q.status !== undefined) rows = rows.filter((r) => r.status === q.status);
	return paginate(rows, q.pageNum, q.pageSize) as PageResult<Role>;
}

export async function getAllRoles(): Promise<Role[]> {
	await delay(80);
	return [...store];
}

export async function createRole(data: Omit<Role, 'id' | 'createTime'>): Promise<Role> {
	await delay(200);
	const r: Role = { ...data, id: nextId(), createTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	store.unshift(r);
	return r;
}

export async function updateRole(id: number, data: Partial<Role>): Promise<Role> {
	await delay(200);
	const idx = store.findIndex((r) => r.id === id);
	if (idx < 0) throw new Error('角色不存在');
	store[idx] = { ...store[idx], ...data, updateTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	return store[idx];
}

export async function removeRole(id: number): Promise<void> {
	await delay(200);
	const idx = store.findIndex((r) => r.id === id);
	if (idx >= 0 && store[idx].code === 'SUPER_ADMIN') throw new Error('超级管理员角色不可删除');
	if (idx >= 0) store.splice(idx, 1);
}

// 角色-权限关联(mock 内存表)
const rolePermissions = new Map<number, number[]>();
rolePermissions.set(1, [2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 18, 20, 21]);
rolePermissions.set(2, [2, 3, 4, 5, 7, 8, 9, 10, 11]);

export async function getRolePermissionIds(id: number): Promise<number[]> {
	await delay(80);
	return [...(rolePermissions.get(id) ?? [])];
}

export async function assignRolePermissions(id: number, permissionIds: number[]): Promise<void> {
	await delay(150);
	rolePermissions.set(id, [...permissionIds]);
}