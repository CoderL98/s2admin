/**
 * 异常日志 API —— mock 实现(只读)
 */
import { delay, paginate } from '$lib/mock/_helpers';
import { mockErrorLogs, type ErrorLog } from '$lib/mock/error-logs';
import type { PageQuery, PageResult } from '$lib/types/auth';

const store: ErrorLog[] = [...mockErrorLogs];

export interface ErrorLogQuery extends PageQuery {
	beginTime?: string;
	endTime?: string;
}

export async function getErrorLogList(q: ErrorLogQuery): Promise<PageResult<ErrorLog>> {
	await delay(250);
	let rows = store;
	if (q.keyword) {
		const kw = q.keyword.toLowerCase();
		rows = rows.filter(
			(e) =>
				(e.traceId ?? '').toLowerCase().includes(kw) ||
				(e.username ?? '').toLowerCase().includes(kw) ||
				e.url.toLowerCase().includes(kw) ||
				e.exception.toLowerCase().includes(kw)
		);
	}
	if (q.beginTime) rows = rows.filter((e) => e.errorTime >= q.beginTime!);
	if (q.endTime) rows = rows.filter((e) => e.errorTime <= q.endTime!);
	rows = [...rows].sort((a, b) => b.errorTime.localeCompare(a.errorTime));
	return paginate(rows, q.pageNum, q.pageSize) as PageResult<ErrorLog>;
}

export async function cleanErrorLog(): Promise<void> {
	await delay(200);
	store.length = 0;
}

export async function getErrorLogById(id: number): Promise<ErrorLog | undefined> {
	await delay(100);
	return store.find((e) => e.id === id);
}