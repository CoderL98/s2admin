/**
 * 用户管理 API —— 真实后端实现
 */
import { authorizedFetch, get, post, put, del, upload } from '$lib/utils/request';
import { env } from '$env/dynamic/public';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { User } from '$lib/mock/users';
import type { ImportResult } from '$lib/types/entities';

const API_BASE_URL = env.PUBLIC_API_BASE_URL || 'http://localhost:8080';

export interface UserQuery extends PageQuery {
	status?: number;
	deptId?: number;
	roleId?: number;
	beginTime?: string;
	endTime?: string;
}

/** 用户提交载荷(新增可带密码,编辑可带角色) */
export type UserPayload = Partial<Omit<User, 'id' | 'createTime'>> & {
	password?: string;
	roleIds?: number[];
};

export async function getUserList(q: UserQuery): Promise<PageResult<User>> {
	return get<PageResult<User>>('/api/system/user', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		status: q.status,
		deptId: q.deptId,
		roleId: q.roleId,
		beginTime: q.beginTime,
		endTime: q.endTime
	});
}

export async function getUserById(id: number): Promise<User> {
	return get<User>('/api/system/user/' + id);
}

export async function createUser(data: UserPayload): Promise<User> {
	return post<User>('/api/system/user', data);
}

export async function updateUser(id: number, data: UserPayload): Promise<User> {
	return put<User>('/api/system/user/' + id, data);
}

export async function removeUser(id: number): Promise<void> {
	return del<void>('/api/system/user/' + id);
}

export async function batchRemoveUsers(ids: number[]): Promise<void> {
	return del<void>('/api/system/user', { ids });
}

export async function updateUserStatus(id: number, status: number): Promise<void> {
	return put<void>('/api/system/user/' + id + '/status', { status });
}

export async function batchUpdateUserStatus(ids: number[], status: number): Promise<void> {
	return put<void>('/api/system/user/status', { ids, status });
}

export async function resetUserPassword(id: number, password: string): Promise<void> {
	return put<void>('/api/system/user/' + id + '/password', { password });
}

async function downloadFile(endpoint: string, filename: string) {
	const res = await authorizedFetch(`${API_BASE_URL}${endpoint}`);
	if (!res.ok) {
		let message = '下载失败';
		try {
			const body = (await res.json()) as { message?: string };
			if (body?.message) message = body.message;
		} catch {
			/* 非 JSON */
		}
		throw new Error(message);
	}
	const blob = await res.blob();
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url;
	a.download = filename;
	a.click();
	URL.revokeObjectURL(url);
}

export async function exportUsers(query: Partial<UserQuery> & { fields?: string } = {}): Promise<void> {
	const { downloadAuthenticated, withQuery } = await import('$lib/utils/download');
	return downloadAuthenticated(
		withQuery('/api/system/user/export', {
			keyword: query.keyword,
			status: query.status,
			deptId: query.deptId,
			roleId: query.roleId,
			beginTime: query.beginTime,
			endTime: query.endTime,
			fields: query.fields
		}),
		'users.csv'
	);
}

export async function downloadUserTemplate(): Promise<void> {
	return downloadFile('/api/system/user/import-template', 'user-import-template.csv');
}

export async function importUsers(file: File): Promise<ImportResult> {
	const fd = new FormData();
	fd.append('file', file);
	return upload<ImportResult>('/api/system/user/import', fd);
}

export async function exportUsersXlsx(query: Partial<UserQuery> & { fields?: string } = {}): Promise<void> {
	const { downloadAuthenticated, withQuery } = await import('$lib/utils/download');
	return downloadAuthenticated(
		withQuery('/api/system/user/export-xlsx', {
			keyword: query.keyword,
			status: query.status,
			deptId: query.deptId,
			roleId: query.roleId,
			beginTime: query.beginTime,
			endTime: query.endTime,
			fields: query.fields
		}),
		'users.xlsx'
	);
}

export async function importUsersXlsx(file: File): Promise<ImportResult> {
	const fd = new FormData();
	fd.append('file', file);
	return upload<ImportResult>('/api/system/user/import-xlsx', fd);
}
