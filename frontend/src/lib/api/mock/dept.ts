import { delay, nextId } from '$lib/mock/_helpers';
import type { Dept } from '$lib/types/entities';

const store: Dept[] = [
	{ id: 1, name: '总经办', parentId: 0, ancestors: '0', sort: 1, status: 0, children: [] }
];

export async function getDeptTree(): Promise<Dept[]> {
	await delay(80);
	return store;
}

export async function getDeptOptions(): Promise<Dept[]> {
	await delay(50);
	return store;
}

export async function createDept(data: Partial<Dept>): Promise<Dept> {
	await delay(80);
	const d: Dept = {
		id: nextId(),
		name: data.name ?? '部门',
		parentId: data.parentId ?? 0,
		sort: data.sort ?? 0,
		status: (data.status ?? 0) as 0 | 1,
		children: []
	};
	store.push(d);
	return d;
}

export async function updateDept(id: number, data: Partial<Dept>): Promise<Dept> {
	await delay(80);
	const idx = store.findIndex((x) => x.id === id);
	if (idx < 0) throw new Error('部门不存在');
	store[idx] = { ...store[idx], ...data };
	return store[idx];
}

export async function removeDept(id: number): Promise<void> {
	await delay(80);
	const i = store.findIndex((x) => x.id === id);
	if (i >= 0) store.splice(i, 1);
}
