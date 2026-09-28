/**
 * 权限管理 API —— 真实后端实现
 */
import { get, post, put, del } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { Permission } from '$lib/mock/permissions';

export interface PermissionQuery extends PageQuery {
	type?: 1 | 2 | 3;
	status?: number;
}

export type PermissionPayload = Partial<Omit<Permission, 'id' | 'createTime'>>;

export interface PermissionTreeNode extends Permission {
	children?: PermissionTreeNode[];
}

export async function getPermissionList(q: PermissionQuery): Promise<PageResult<Permission>> {
	return get<PageResult<Permission>>('/api/system/permission', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		type: q.type,
		status: q.status
	});
}

export async function getPermissionTree(): Promise<PermissionTreeNode[]> {
	return get<PermissionTreeNode[]>('/api/system/permission/tree');
}

export async function getAllPermissions(): Promise<Permission[]> {
	return get<Permission[]>('/api/system/permission/all');
}

export async function createPermission(data: PermissionPayload): Promise<Permission> {
	return post<Permission>('/api/system/permission', data);
}

export async function updatePermission(id: number, data: PermissionPayload): Promise<Permission> {
	return put<Permission>('/api/system/permission/' + id, data);
}

export async function removePermission(id: number): Promise<void> {
	return del<void>('/api/system/permission/' + id);
}
