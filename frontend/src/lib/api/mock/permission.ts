/**
 * 权限管理 API —— mock 实现
 */
import { delay, paginate, nextId } from '$lib/mock/_helpers';
import { mockPermissions, type Permission } from '$lib/mock/permissions';
import type { PageQuery, PageResult } from '$lib/types/auth';

const store: Permission[] = [...mockPermissions];

export interface PermissionQuery extends PageQuery {
	type?: 1 | 2 | 3;
	status?: number;
}

export async function getPermissionList(q: PermissionQuery): Promise<PageResult<Permission>> {
	await delay(200);
	let rows = store;
	if (q.keyword) {
		const kw = q.keyword.toLowerCase();
		rows = rows.filter((p) => p.name.toLowerCase().includes(kw) || p.code.toLowerCase().includes(kw));
	}
	if (q.type !== undefined) rows = rows.filter((p) => p.type === q.type);
	if (q.status !== undefined) rows = rows.filter((p) => p.status === q.status);
	return paginate(rows, q.pageNum, q.pageSize) as PageResult<Permission>;
}

export async function getAllPermissions(): Promise<Permission[]> {
	await delay(80);
	return [...store];
}

export async function getPermissionTree(): Promise<Permission[]> {
	await delay(80);
	return [...store];
}

export async function createPermission(data: Omit<Permission, 'id' | 'createTime'>): Promise<Permission> {
	await delay(200);
	const p: Permission = { ...data, id: nextId(), createTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	store.unshift(p);
	return p;
}

export async function updatePermission(id: number, data: Partial<Permission>): Promise<Permission> {
	await delay(200);
	const idx = store.findIndex((p) => p.id === id);
	if (idx < 0) throw new Error('权限不存在');
	store[idx] = { ...store[idx], ...data, updateTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	return store[idx];
}

export async function removePermission(id: number): Promise<void> {
	await delay(200);
	const hasChildren = store.some((p) => p.parentId === id);
	if (hasChildren) throw new Error('存在子权限,不可删除');
	const idx = store.findIndex((p) => p.id === id);
	if (idx >= 0) store.splice(idx, 1);
}