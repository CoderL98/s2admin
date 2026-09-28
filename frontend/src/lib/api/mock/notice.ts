import { delay, paginate, nextId } from '$lib/mock/_helpers';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { Notice } from '$lib/types/entities';

const store: Notice[] = [
	{
		id: 1,
		title: '欢迎使用 S2Admin',
		content: '这是一条 mock 公告',
		type: 2,
		status: 1,
		pinned: 1,
		publishTime: '2026-08-26 10:00:00'
	}
];

export async function getNoticeList(q: PageQuery): Promise<PageResult<Notice>> {
	await delay(80);
	return paginate(store, q.pageNum, q.pageSize);
}

export async function getPublishedNotices(): Promise<Notice[]> {
	await delay(50);
	return store.filter((n) => n.status === 1);
}

export async function createNotice(data: Partial<Notice>): Promise<Notice> {
	await delay(80);
	const n: Notice = {
		id: nextId(),
		title: data.title ?? '公告',
		content: data.content,
		type: (data.type ?? 1) as 1 | 2,
		status: (data.status ?? 0) as 0 | 1,
		pinned: (data.pinned ?? 0) as 0 | 1
	};
	store.unshift(n);
	return n;
}

export async function updateNotice(id: number, data: Partial<Notice>): Promise<Notice> {
	await delay(80);
	const i = store.findIndex((x) => x.id === id);
	if (i < 0) throw new Error('公告不存在');
	store[i] = { ...store[i], ...data };
	return store[i];
}

export async function publishNotice(id: number, published: boolean): Promise<Notice> {
	return updateNotice(id, { status: published ? 1 : 0 });
}

export async function removeNotice(id: number): Promise<void> {
	await delay(50);
	const i = store.findIndex((x) => x.id === id);
	if (i >= 0) store.splice(i, 1);
}
