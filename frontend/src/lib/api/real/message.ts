import { get, post, put, del } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { InboxMessage } from '$lib/types/entities';

export interface MessageQuery extends PageQuery {
	readFlag?: number;
	keyword?: string;
}

export async function sendMessage(title: string, content: string, receiverIds: number[]): Promise<void> {
	return post<void>('/api/system/message', { title, content, receiverIds });
}

export async function getMyMessages(q: MessageQuery): Promise<PageResult<InboxMessage>> {
	return get<PageResult<InboxMessage>>('/api/system/message/my', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		readFlag: q.readFlag,
		keyword: q.keyword
	});
}

export async function getUnreadCount(): Promise<number> {
	const r = await get<{ count: number }>('/api/system/message/unread-count');
	return r.count;
}

export async function markMessageRead(id: number): Promise<void> {
	return put<void>('/api/system/message/' + id + '/read', {});
}

export async function markAllMessagesRead(): Promise<void> {
	return put<void>('/api/system/message/read-all', {});
}

export async function deleteMessage(id: number): Promise<void> {
	return del<void>('/api/system/message/' + id);
}
