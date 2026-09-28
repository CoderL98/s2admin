/**
 * 登录日志 API —— 真实后端实现(只读)
 */
import { get, del } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { LoginLog } from '$lib/mock/login-logs';

export interface LoginLogQuery extends PageQuery {
	status?: 0 | 1;
	beginTime?: string;
	endTime?: string;
}

export async function getLoginLogList(q: LoginLogQuery): Promise<PageResult<LoginLog>> {
	return get<PageResult<LoginLog>>('/api/monitor/login-log', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		status: q.status,
		beginTime: q.beginTime,
		endTime: q.endTime
	});
}

export async function cleanLoginLog(): Promise<void> {
	return del<void>('/api/monitor/login-log/clean');
}

export async function exportLoginLog(q: Partial<LoginLogQuery> = {}): Promise<void> {
	const { downloadAuthenticated, withQuery } = await import('$lib/utils/download');
	return downloadAuthenticated(
		withQuery('/api/monitor/login-log/export', {
			keyword: q.keyword,
			status: q.status,
			beginTime: q.beginTime,
			endTime: q.endTime
		}),
		'login-log.csv'
	);
}
