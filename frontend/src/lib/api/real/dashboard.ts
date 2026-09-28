import { get } from '$lib/utils/request';
import type { DashboardStats } from '$lib/types/entities';

export async function getDashboardStats(): Promise<DashboardStats> {
	return get<DashboardStats>('/api/system/dashboard/stats');
}
