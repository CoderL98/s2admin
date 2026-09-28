import type { PageQuery, PageResult } from '$lib/types/auth';
import type { Tenant } from '$lib/types/entities';
import type { TenantPayload } from '../real/tenant';

export async function getTenantList(q: PageQuery): Promise<PageResult<Tenant>> {
	return { records: [], total: 0, size: q.pageSize, current: q.pageNum, pages: 0 };
}
export async function getTenantOptions(): Promise<Tenant[]> {
	return [];
}
export async function createTenant(data: TenantPayload): Promise<Tenant> {
	return { id: 1, name: data.name, code: data.code, status: 0 };
}
export async function updateTenant(id: number, data: TenantPayload): Promise<Tenant> {
	return { id, name: data.name, code: data.code, status: (data.status ?? 0) as 0 | 1 };
}
export async function removeTenant(_id: number): Promise<void> {}
