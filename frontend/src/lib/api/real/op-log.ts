/**
 * 操作日志 API —— 真实后端实现(只读)
 */
import { get, del } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { OperationLog } from '$lib/mock/op-logs';

export interface OpLogQuery extends PageQuery {
	status?: 0 | 1;
	module?: string;
	beginTime?: string;
	endTime?: string;
}

export async function getOpLogList(q: OpLogQuery): Promise<PageResult<OperationLog>> {
	return get<PageResult<OperationLog>>('/api/monitor/op-log', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		status: q.status,
		module: q.module,
		beginTime: q.beginTime,
		endTime: q.endTime
	});
}

export async function getOpLogById(id: number): Promise<OperationLog> {
	return get<OperationLog>('/api/monitor/op-log/' + id);
}

export async function cleanOpLog(): Promise<void> {
	return del<void>('/api/monitor/op-log/clean');
}

export async function exportOpLog(q: Partial<OpLogQuery> = {}): Promise<void> {
	const { downloadAuthenticated, withQuery } = await import('$lib/utils/download');
	return downloadAuthenticated(
		withQuery('/api/monitor/op-log/export', {
			keyword: q.keyword,
			status: q.status,
			module: q.module,
			beginTime: q.beginTime,
			endTime: q.endTime
		}),
		'operation-log.csv'
	);
}
