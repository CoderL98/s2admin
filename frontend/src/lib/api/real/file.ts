import { get, del, upload, authorizedFetch } from '$lib/utils/request';
import { env } from '$env/dynamic/public';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { StoredFile } from '$lib/types/entities';

const API_BASE_URL = env.PUBLIC_API_BASE_URL || 'http://localhost:8080';

export interface FileQuery extends PageQuery {
	category?: string;
}

export async function getFileList(q: FileQuery): Promise<PageResult<StoredFile>> {
	return get<PageResult<StoredFile>>('/api/system/file', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		category: q.category
	});
}

export async function uploadFile(file: File, category?: string): Promise<StoredFile> {
	const fd = new FormData();
	fd.append('file', file);
	if (category) fd.append('category', category);
	return upload<StoredFile>('/api/system/file/upload', fd);
}

export async function removeFile(id: number): Promise<void> {
	return del<void>('/api/system/file/id/' + id);
}

export async function downloadStoredFile(storedName: string, filename: string): Promise<void> {
	const res = await authorizedFetch(`${API_BASE_URL}/api/system/file/${encodeURIComponent(storedName)}`);
	if (!res.ok) throw new Error('下载失败');
	const blob = await res.blob();
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url;
	a.download = filename;
	a.click();
	URL.revokeObjectURL(url);
}
