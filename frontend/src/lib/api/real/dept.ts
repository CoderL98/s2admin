import { get, post, put, del } from '$lib/utils/request';
import type { Dept } from '$lib/types/entities';

export type DeptPayload = Partial<Omit<Dept, 'id' | 'children' | 'createTime'>>;

export async function getDeptTree(): Promise<Dept[]> {
	return get<Dept[]>('/api/system/dept/tree');
}

export async function getDeptOptions(): Promise<Dept[]> {
	return get<Dept[]>('/api/system/dept/options');
}

export async function createDept(data: DeptPayload): Promise<Dept> {
	return post<Dept>('/api/system/dept', data);
}

export async function updateDept(id: number, data: DeptPayload): Promise<Dept> {
	return put<Dept>('/api/system/dept/' + id, data);
}

export async function removeDept(id: number): Promise<void> {
	return del<void>('/api/system/dept/' + id);
}
