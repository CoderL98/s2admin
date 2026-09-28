/**
 * 操作日志 API —— mock 实现(只读)
 */
import { delay, paginate } from '$lib/mock/_helpers';
import { mockOpLogs, type OperationLog } from '$lib/mock/op-logs';
import type { PageQuery, PageResult } from '$lib/types/auth';

const store: OperationLog[] = [...mockOpLogs];

export interface OpLogQuery extends PageQuery {
	status?: 0 | 1;
	module?: string;
	beginTime?: string;
	endTime?: string;
}

export async function cleanOpLog(): Promise<void> {
	await delay(200);
	store.length = 0;
}

export async function getOpLogList(q: OpLogQuery): Promise<PageResult<OperationLog>> {
	await delay(250);
	let rows = store;
	if (q.keyword) {
		const kw = q.keyword.toLowerCase();
		rows = rows.filter(
			(o) =>
				o.username.toLowerCase().includes(kw) ||
				o.module.toLowerCase().includes(kw) ||
				o.url.toLowerCase().includes(kw)
		);
	}
	if (q.module) rows = rows.filter((o) => o.module === q.module);
	if (q.status !== undefined) rows = rows.filter((o) => o.status === q.status);
	if (q.beginTime) rows = rows.filter((o) => o.operationTime >= q.beginTime!);
	if (q.endTime) rows = rows.filter((o) => o.operationTime <= q.endTime!);
	rows = [...rows].sort((a, b) => b.operationTime.localeCompare(a.operationTime));
	return paginate(rows, q.pageNum, q.pageSize) as PageResult<OperationLog>;
}

export async function getOpLogById(id: number): Promise<OperationLog | undefined> {
	await delay(80);
	return store.find((o) => o.id === id);
}

export async function exportOpLog(): Promise<void> {
	await delay(80);
}