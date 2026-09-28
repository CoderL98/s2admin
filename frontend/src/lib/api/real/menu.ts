/**
 * 菜单管理 API —— 真实后端实现
 */
import { get, post, put, del } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { Menu } from '$lib/mock/menus';

export interface MenuQuery extends PageQuery {
	type?: 1 | 2 | 3;
	status?: number;
}

export type MenuPayload = Partial<Omit<Menu, 'id' | 'createTime'>>;

/** 树形菜单(children 子节点) */
export interface MenuTreeNode extends Menu {
	children?: MenuTreeNode[];
}

export async function getMenuList(q: MenuQuery): Promise<PageResult<Menu>> {
	return get<PageResult<Menu>>('/api/system/menu', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		type: q.type,
		status: q.status
	});
}

export async function getMenuTree(): Promise<MenuTreeNode[]> {
	return get<MenuTreeNode[]>('/api/system/menu/tree');
}

export async function createMenu(data: MenuPayload): Promise<Menu> {
	return post<Menu>('/api/system/menu', data);
}

export async function updateMenu(id: number, data: MenuPayload): Promise<Menu> {
	return put<Menu>('/api/system/menu/' + id, data);
}

export async function removeMenu(id: number): Promise<void> {
	return del<void>('/api/system/menu/' + id);
}

export async function moveMenu(id: number, direction: 'up' | 'down'): Promise<void> {
	return put<void>('/api/system/menu/' + id + '/move?direction=' + direction, {});
}
