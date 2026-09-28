import { get, post, put, del } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { Notice } from '$lib/types/entities';

export interface NoticeQuery extends PageQuery {
	status?: number;
	type?: number;
}

export type NoticePayload = Partial<Omit<Notice, 'id' | 'createTime' | 'publishTime'>>;

export async function getNoticeList(q: NoticeQuery): Promise<PageResult<Notice>> {
	return get<PageResult<Notice>>('/api/system/notice', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		status: q.status,
		type: q.type
	});
}

export async function getPublishedNotices(): Promise<Notice[]> {
	return get<Notice[]>('/api/system/notice/published');
}

export async function createNotice(data: NoticePayload): Promise<Notice> {
	return post<Notice>('/api/system/notice', data);
}

export async function updateNotice(id: number, data: NoticePayload): Promise<Notice> {
	return put<Notice>('/api/system/notice/' + id, data);
}

export async function publishNotice(id: number, published: boolean): Promise<Notice> {
	return put<Notice>('/api/system/notice/' + id + '/publish', { published });
}

export async function removeNotice(id: number): Promise<void> {
	return del<void>('/api/system/notice/' + id);
}
