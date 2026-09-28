/**
 * 用户管理 API —— mock 实现
 */
import { delay, paginate, nextId } from '$lib/mock/_helpers';
import { mockUsers, type User } from '$lib/mock/users';
import type { PageQuery, PageResult } from '$lib/types/auth';

// 内存中保留最新数据,create/update/remove 会改动它
const store: User[] = [...mockUsers];

export interface UserQuery extends PageQuery {
	status?: number;
	deptId?: number;
	roleId?: number;
	beginTime?: string;
	endTime?: string;
}

export async function getUserList(q: UserQuery): Promise<PageResult<User>> {
	await delay(250);
	let rows = store;
	if (q.keyword) {
		const kw = q.keyword.toLowerCase();
		rows = rows.filter(
			(u) =>
				u.username.toLowerCase().includes(kw) ||
				u.nickname.toLowerCase().includes(kw) ||
				(u.email ?? '').toLowerCase().includes(kw) ||
				(u.phone ?? '').includes(kw)
		);
	}
	if (q.status !== undefined) rows = rows.filter((u) => u.status === q.status);
	if (q.deptId !== undefined) rows = rows.filter((u) => u.deptId === q.deptId);
	if (q.roleId !== undefined) rows = rows.filter((u) => u.roleIds?.includes(q.roleId!));
	if (q.beginTime) rows = rows.filter((u) => (u.createTime ?? '') >= q.beginTime!);
	if (q.endTime) rows = rows.filter((u) => (u.createTime ?? '') <= q.endTime!);
	return paginate(rows, q.pageNum, q.pageSize) as PageResult<User>;
}

export async function getUserById(id: number): Promise<User | undefined> {
	await delay(100);
	return store.find((u) => u.id === id);
}

export async function createUser(data: Partial<User> & Pick<User, 'username' | 'nickname'>): Promise<User> {
	await delay(200);
	const user: User = {
		email: '',
		phone: '',
		status: 0,
		...data,
		id: nextId(),
		createTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
	};
	store.unshift(user);
	return user;
}

export async function updateUser(id: number, data: Partial<User>): Promise<User> {
	await delay(200);
	const idx = store.findIndex((u) => u.id === id);
	if (idx < 0) throw new Error('用户不存在');
	store[idx] = { ...store[idx], ...data, updateTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	return store[idx];
}

export async function removeUser(id: number): Promise<void> {
	await delay(200);
	const idx = store.findIndex((u) => u.id === id);
	if (idx >= 0) store.splice(idx, 1);
}

export async function batchRemoveUsers(ids: number[]): Promise<void> {
	await delay(200);
	for (let i = store.length - 1; i >= 0; i--) {
		if (ids.includes(store[i].id)) store.splice(i, 1);
	}
}

export async function updateUserStatus(id: number, status: number): Promise<void> {
	await delay(150);
	const idx = store.findIndex((u) => u.id === id);
	if (idx >= 0) {
		store[idx].status = status as User['status'];
	}
}

export async function batchUpdateUserStatus(ids: number[], status: number): Promise<void> {
	await delay(150);
	for (const id of ids) {
		const idx = store.findIndex((u) => u.id === id);
		if (idx >= 0) store[idx].status = status as User['status'];
	}
}

export async function resetUserPassword(id: number, password: string): Promise<void> {
	await delay(150);
	// mock 不保存密码,仅模拟成功
	void id;
	void password;
}

const EXPORT_KEYS = ['username', 'nickname', 'email', 'phone', 'deptName', 'status'] as const;

export async function exportUsers(query: { fields?: string } = {}): Promise<void> {
	await delay(80);
	const cols = (query.fields || EXPORT_KEYS.join(','))
		.split(',')
		.map((c) => c.trim())
		.filter((c) => (EXPORT_KEYS as readonly string[]).includes(c));
	const keys = cols.length ? cols : [...EXPORT_KEYS];
	const csv =
		'\uFEFF' +
		keys.join(',') +
		'\n' +
		store
			.map((u) =>
				keys
					.map((k) => {
						const record = u as unknown as Record<string, unknown>;
						return record[k] ?? '';
					})
					.join(',')
			)
			.join('\n');
	const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url;
	a.download = 'users.csv';
	a.click();
	URL.revokeObjectURL(url);
}

export async function downloadUserTemplate(): Promise<void> {
	await delay(40);
	const csv = '\uFEFFusername,nickname,email,phone,deptName,status\nadmin_demo,示例用户,demo@example.com,13800138000,研发部,0\n';
	const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url;
	a.download = 'user-import-template.csv';
	a.click();
	URL.revokeObjectURL(url);
}

export async function importUsers(file: File): Promise<{ created: number; skipped: number; errors: string[] }> {
	await delay(200);
	void file;
	return { created: 0, skipped: 0, errors: ['Mock 模式不写入真实数据'] };
}