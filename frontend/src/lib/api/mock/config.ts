/**
 * 系统配置 API —— mock 实现
 */
import { delay, paginate, nextId } from '$lib/mock/_helpers';
import { mockConfigs, type SysConfig } from '$lib/mock/configs';
import type { PageQuery, PageResult } from '$lib/types/auth';

const store: SysConfig[] = [...mockConfigs];

export interface ConfigQuery extends PageQuery {
	groupCode?: string;
	configType?: string;
}

export async function getConfigList(q: ConfigQuery): Promise<PageResult<SysConfig>> {
	await delay(200);
	let rows = store;
	if (q.keyword) {
		const kw = q.keyword.toLowerCase();
		rows = rows.filter((c) => c.configKey.toLowerCase().includes(kw) || c.configValue.toLowerCase().includes(kw));
	}
	if (q.groupCode) rows = rows.filter((c) => c.groupCode === q.groupCode);
	if (q.configType) rows = rows.filter((c) => c.configType === q.configType);
	return paginate(rows, q.pageNum, q.pageSize) as PageResult<SysConfig>;
}

export async function getConfigGroups(): Promise<string[]> {
	await delay(50);
	return [...new Set(store.map((c) => c.groupCode).filter(Boolean))];
}

export async function getConfigByKey(key: string): Promise<SysConfig | undefined> {
	await delay(50);
	return store.find((c) => c.configKey === key);
}

export async function createConfig(data: Omit<SysConfig, 'id' | 'createTime'>): Promise<SysConfig> {
	await delay(200);
	if (store.some((c) => c.configKey === data.configKey)) throw new Error('配置键已存在');
	const c: SysConfig = { ...data, id: nextId(), createTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	store.unshift(c);
	return c;
}

export async function updateConfig(id: number, data: Partial<SysConfig>): Promise<SysConfig> {
	await delay(200);
	const idx = store.findIndex((c) => c.id === id);
	if (idx < 0) throw new Error('配置不存在');
	store[idx] = { ...store[idx], ...data, updateTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	return store[idx];
}

export async function removeConfig(id: number): Promise<void> {
	await delay(200);
	const idx = store.findIndex((c) => c.id === id);
	if (idx >= 0) store.splice(idx, 1);
}