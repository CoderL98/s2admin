import { delay, paginate, nextId } from '$lib/mock/_helpers';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { StoredFile } from '$lib/types/entities';

const store: StoredFile[] = [];

export async function getFileList(q: PageQuery & { category?: string }): Promise<PageResult<StoredFile>> {
	await delay(80);
	let rows = store;
	if (q.keyword) rows = rows.filter((f) => f.name.includes(String(q.keyword)));
	if (q.category) rows = rows.filter((f) => f.category === q.category);
	return paginate(rows, q.pageNum, q.pageSize);
}

export async function uploadFile(file: File, category?: string): Promise<StoredFile> {
	await delay(80);
	const row: StoredFile = {
		id: nextId(),
		name: file.name,
		url: URL.createObjectURL(file),
		size: file.size,
		category: category ?? 'default',
		contentType: file.type,
		storageType: 'local'
	};
	store.unshift(row);
	return row;
}

export async function removeFile(id: number): Promise<void> {
	await delay(50);
	const i = store.findIndex((x) => x.id === id);
	if (i >= 0) store.splice(i, 1);
}

export async function downloadStoredFile(): Promise<void> {
	await delay(50);
}
