/**
 * 菜单管理 API —— mock 实现
 */
import { delay, paginate, nextId } from '$lib/mock/_helpers';
import { mockMenus, type Menu } from '$lib/mock/menus';
import type { PageQuery, PageResult } from '$lib/types/auth';
import { buildTree } from '$lib/utils/tree';

const store: Menu[] = [...mockMenus];

export interface MenuQuery extends PageQuery {
	type?: 1 | 2 | 3;
	status?: number;
}

export async function getMenuList(q: MenuQuery): Promise<PageResult<Menu>> {
	await delay(200);
	let rows = store;
	if (q.keyword) {
		const kw = q.keyword.toLowerCase();
		rows = rows.filter((m) => m.name.toLowerCase().includes(kw) || m.path.toLowerCase().includes(kw));
	}
	if (q.type !== undefined) rows = rows.filter((m) => m.type === q.type);
	if (q.status !== undefined) rows = rows.filter((m) => m.status === q.status);
	return paginate(rows, q.pageNum, q.pageSize) as PageResult<Menu>;
}

export async function getMenuTree(): Promise<Menu[]> {
	await delay(150);
	return buildTree(store.filter((m) => m.type !== 3));
}

export async function createMenu(data: Omit<Menu, 'id' | 'createTime'>): Promise<Menu> {
	await delay(200);
	const m: Menu = { ...data, id: nextId(), createTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	store.unshift(m);
	return m;
}

export async function updateMenu(id: number, data: Partial<Menu>): Promise<Menu> {
	await delay(200);
	const idx = store.findIndex((m) => m.id === id);
	if (idx < 0) throw new Error('菜单不存在');
	store[idx] = { ...store[idx], ...data, updateTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	return store[idx];
}

export async function moveMenu(id: number, direction: 'up' | 'down'): Promise<void> {
	await delay(80);
	const current = store.find((m) => m.id === id);
	if (!current) throw new Error('菜单不存在');
	const siblings = store
		.filter((m) => m.parentId === current.parentId)
		.sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id);
	const idx = siblings.findIndex((m) => m.id === id);
	const swap = idx + (direction === 'up' ? -1 : 1);
	if (idx < 0 || swap < 0 || swap >= siblings.length) return;
	const a = siblings[idx].sort ?? 0;
	const b = siblings[swap].sort ?? 0;
	siblings[idx].sort = b;
	siblings[swap].sort = a;
}

export async function removeMenu(id: number): Promise<void> {
	await delay(200);
	const hasChildren = store.some((m) => m.parentId === id);
	if (hasChildren) throw new Error('存在子菜单,不可删除');
	const idx = store.findIndex((m) => m.id === id);
	if (idx >= 0) store.splice(idx, 1);
}