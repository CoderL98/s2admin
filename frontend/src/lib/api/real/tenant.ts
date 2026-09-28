import { del, get, post, put } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { Tenant } from '$lib/types/entities';

export interface TenantPayload {
	name: string;
	code: string;
	status?: number;
	contact?: string;
	remark?: string;
}

export async function getTenantList(q: PageQuery): Promise<PageResult<Tenant>> {
	return get<PageResult<Tenant>>('/api/system/tenant', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword
	});
}

export async function getTenantOptions(): Promise<Tenant[]> {
	return get<Tenant[]>('/api/system/tenant/options');
}

export async function createTenant(data: TenantPayload): Promise<Tenant> {
	return post<Tenant>('/api/system/tenant', data);
}

export async function updateTenant(id: number, data: TenantPayload): Promise<Tenant> {
	return put<Tenant>('/api/system/tenant/' + id, data);
}

export async function removeTenant(id: number): Promise<void> {
	return del<void>('/api/system/tenant/' + id);
}
