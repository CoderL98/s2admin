/**
 * 登录日志 API —— mock 实现(只读)
 */
import { delay, paginate } from '$lib/mock/_helpers';
import { mockLoginLogs, type LoginLog } from '$lib/mock/login-logs';
import type { PageQuery, PageResult } from '$lib/types/auth';

const store: LoginLog[] = [...mockLoginLogs];

export interface LoginLogQuery extends PageQuery {
	status?: 0 | 1;
	beginTime?: string;
	endTime?: string;
}

export async function getLoginLogList(q: LoginLogQuery): Promise<PageResult<LoginLog>> {
	await delay(250);
	let rows = store;
	if (q.keyword) {
		const kw = q.keyword.toLowerCase();
		rows = rows.filter((l) => l.username.toLowerCase().includes(kw) || l.ip.includes(kw));
	}
	if (q.status !== undefined) rows = rows.filter((l) => l.status === q.status);
	if (q.beginTime) rows = rows.filter((l) => l.loginTime >= q.beginTime!);
	if (q.endTime) rows = rows.filter((l) => l.loginTime <= q.endTime!);
	rows = [...rows].sort((a, b) => b.loginTime.localeCompare(a.loginTime));
	return paginate(rows, q.pageNum, q.pageSize) as PageResult<LoginLog>;
}

export async function cleanLoginLog(): Promise<void> {
	await delay(200);
	store.length = 0;
}

export async function exportLoginLog(): Promise<void> {
	await delay(80);
}