import { delay } from '$lib/mock/_helpers';
import { mockUsers } from '$lib/mock/users';
import { mockRoles } from '$lib/mock/roles';
import { mockLoginLogs } from '$lib/mock/login-logs';
import { mockErrorLogs } from '$lib/mock/error-logs';
import type { DashboardStats } from '$lib/types/entities';

export async function getDashboardStats(): Promise<DashboardStats> {
	await delay(120);
	const today = new Date().toISOString().slice(0, 10);
	return {
		userCount: mockUsers.length,
		roleCount: mockRoles.length,
		todayLoginCount: mockLoginLogs.filter((l) => l.loginTime.startsWith(today)).length,
		errorCount: mockErrorLogs.length
	};
}
