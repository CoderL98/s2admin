/**
 * 角色管理 API —— 真实后端实现
 */
import { get, post, put, del } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { Role } from '$lib/mock/roles';

export interface RoleQuery extends PageQuery {
	status?: number;
}

export type RolePayload = Partial<Omit<Role, 'id' | 'createTime'>> & {
	permissionIds?: number[];
};

export async function getRoleList(q: RoleQuery): Promise<PageResult<Role>> {
	return get<PageResult<Role>>('/api/system/role', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		status: q.status
	});
}

export async function getAllRoles(): Promise<Role[]> {
	return get<Role[]>('/api/system/role/all');
}

export async function createRole(data: RolePayload): Promise<Role> {
	return post<Role>('/api/system/role', data);
}

export async function updateRole(id: number, data: RolePayload): Promise<Role> {
	return put<Role>('/api/system/role/' + id, data);
}

export async function removeRole(id: number): Promise<void> {
	return del<void>('/api/system/role/' + id);
}

export async function getRolePermissionIds(id: number): Promise<number[]> {
	return get<number[]>('/api/system/role/' + id + '/permissions');
}

export async function assignRolePermissions(id: number, permissionIds: number[]): Promise<void> {
	return put<void>('/api/system/role/' + id + '/permissions', { permissionIds });
}
