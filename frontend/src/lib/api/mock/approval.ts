import type { PageQuery, PageResult } from '$lib/types/auth';
import type { ApprovalItem } from '$lib/types/entities';

const empty = (q: PageQuery): PageResult<ApprovalItem> => ({
	records: [],
	total: 0,
	size: q.pageSize ?? 10,
	current: q.pageNum ?? 1,
	pages: 0
});

export async function getMyApprovals(q: PageQuery) {
	return empty(q);
}
export async function getPendingApprovals(q: PageQuery) {
	return empty(q);
}
export async function getApproval(id: number): Promise<ApprovalItem> {
	return { id, flowId: 1, title: '模拟审批', applicantId: 1, status: 1 };
}
export async function submitApproval(data: { flowId: number; title: string; content?: string }) {
	return getApproval(1).then((item) => ({ ...item, ...data }));
}
export async function approveApproval(id: number) {
	return getApproval(id);
}
export async function rejectApproval(id: number) {
	return getApproval(id);
}
export async function cancelApproval(id: number) {
	return getApproval(id);
}
