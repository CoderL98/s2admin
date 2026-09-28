import { delay, paginate, nextId } from '$lib/mock/_helpers';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { InboxMessage } from '$lib/types/entities';

const store: InboxMessage[] = [
	{
		id: 1,
		title: '欢迎',
		content: '这是一条 mock 站内信',
		senderName: '系统',
		receiverId: 1,
		readFlag: 0
	}
];

export async function sendMessage(): Promise<void> {
	await delay(80);
}

export async function getMyMessages(q: PageQuery & { keyword?: string }): Promise<PageResult<InboxMessage>> {
	await delay(80);
	let rows = store;
	if (q.keyword) rows = rows.filter((m) => m.title.includes(String(q.keyword)));
	return paginate(rows, q.pageNum, q.pageSize);
}

export async function getUnreadCount(): Promise<number> {
	await delay(30);
	return store.filter((m) => m.readFlag === 0).length;
}

export async function markMessageRead(id: number): Promise<void> {
	await delay(40);
	const m = store.find((x) => x.id === id);
	if (m) m.readFlag = 1;
}

export async function markAllMessagesRead(): Promise<void> {
	await delay(40);
	store.forEach((m) => (m.readFlag = 1));
}

export async function deleteMessage(id: number): Promise<void> {
	await delay(40);
	const i = store.findIndex((x) => x.id === id);
	if (i >= 0) store.splice(i, 1);
}

