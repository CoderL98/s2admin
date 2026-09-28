import type { PageQuery, PageResult } from '$lib/types/auth';
import type { FlowDef } from '$lib/types/entities';
import type { FlowPayload } from '../real/flow';

const empty = (q: PageQuery): PageResult<FlowDef> => ({
	records: [],
	total: 0,
	size: q.pageSize ?? 10,
	current: q.pageNum ?? 1,
	pages: 0
});

export async function getFlowList(q: PageQuery): Promise<PageResult<FlowDef>> {
	return empty(q);
}
export async function getFlowOptions(): Promise<FlowDef[]> {
	return [];
}
export async function getFlow(id: number): Promise<FlowDef> {
	return { id, name: '模拟流程', code: 'MOCK', status: 0 };
}
export async function createFlow(data: FlowPayload): Promise<FlowDef> {
	return { id: 1, name: data.name, code: data.code, status: 0 };
}
export async function updateFlow(id: number, data: FlowPayload): Promise<FlowDef> {
	return { id, name: data.name, code: data.code, status: (data.status ?? 0) as 0 | 1 };
}
export async function removeFlow(_id: number): Promise<void> {}
