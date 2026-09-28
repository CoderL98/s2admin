import { get, post } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { ApprovalItem } from '$lib/types/entities';

export async function getMyApprovals(q: PageQuery): Promise<PageResult<ApprovalItem>> {
	return get<PageResult<ApprovalItem>>('/api/workflow/approval/mine', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword
	});
}

export async function getPendingApprovals(q: PageQuery): Promise<PageResult<ApprovalItem>> {
	return get<PageResult<ApprovalItem>>('/api/workflow/approval/pending', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword
	});
}

export async function getApproval(id: number): Promise<ApprovalItem> {
	return get<ApprovalItem>('/api/workflow/approval/' + id);
}

export async function submitApproval(data: {
	flowId: number;
	title: string;
	content?: string;
}): Promise<ApprovalItem> {
	return post<ApprovalItem>('/api/workflow/approval', data);
}

export async function approveApproval(id: number, comment?: string): Promise<ApprovalItem> {
	return post<ApprovalItem>('/api/workflow/approval/' + id + '/approve', { comment });
}

export async function rejectApproval(id: number, comment: string): Promise<ApprovalItem> {
	return post<ApprovalItem>('/api/workflow/approval/' + id + '/reject', { comment });
}

export async function cancelApproval(id: number, comment?: string): Promise<ApprovalItem> {
	return post<ApprovalItem>('/api/workflow/approval/' + id + '/cancel', { comment });
}
