/**
 * 异常日志 API —— 真实后端实现(只读)
 */
import { get, del } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { ErrorLog } from '$lib/mock/error-logs';

export interface ErrorLogQuery extends PageQuery {
	beginTime?: string;
	endTime?: string;
}

export async function getErrorLogList(q: ErrorLogQuery): Promise<PageResult<ErrorLog>> {
	return get<PageResult<ErrorLog>>('/api/monitor/error-log', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		beginTime: q.beginTime,
		endTime: q.endTime
	});
}

export async function getErrorLogById(id: number): Promise<ErrorLog> {
	return get<ErrorLog>('/api/monitor/error-log/' + id);
}

export async function cleanErrorLog(): Promise<void> {
	return del<void>('/api/monitor/error-log/clean');
}
