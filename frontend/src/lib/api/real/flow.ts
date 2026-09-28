import { del, get, post, put } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { FlowDef, FlowNode } from '$lib/types/entities';

export interface FlowPayload {
	name: string;
	code: string;
	status?: number;
	remark?: string;
	nodes: Array<
		Pick<FlowNode, 'name' | 'roleId' | 'nodeType' | 'signMode' | 'rejectTo' | 'conditionExpr' | 'yesSeq' | 'noSeq'>
	>;
}

export async function getFlowList(q: PageQuery): Promise<PageResult<FlowDef>> {
	return get<PageResult<FlowDef>>('/api/workflow/flow', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword
	});
}

export async function getFlowOptions(): Promise<FlowDef[]> {
	return get<FlowDef[]>('/api/workflow/flow/options');
}

export async function getFlow(id: number): Promise<FlowDef> {
	return get<FlowDef>('/api/workflow/flow/' + id);
}

export async function createFlow(data: FlowPayload): Promise<FlowDef> {
	return post<FlowDef>('/api/workflow/flow', data);
}

export async function updateFlow(id: number, data: FlowPayload): Promise<FlowDef> {
	return put<FlowDef>('/api/workflow/flow/' + id, data);
}

export async function removeFlow(id: number): Promise<void> {
	return del<void>('/api/workflow/flow/' + id);
}
