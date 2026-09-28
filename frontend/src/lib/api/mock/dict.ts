/**
 * 字典管理 API —— mock 实现
 */
import { delay, paginate, nextId } from '$lib/mock/_helpers';
import { mockDictTypes, type DictType } from '$lib/mock/dict-types';
import { mockDictData, type DictData } from '$lib/mock/dict-data';
import type { PageQuery, PageResult } from '$lib/types/auth';

const typeStore: DictType[] = [...mockDictTypes];
const dataStore: DictData[] = [...mockDictData];

// ========== 字典类型 ==========

export interface DictTypeQuery extends PageQuery {
	status?: number;
}

export async function getDictTypeList(q: DictTypeQuery): Promise<PageResult<DictType>> {
	await delay(200);
	let rows = typeStore;
	if (q.keyword) {
		const kw = q.keyword.toLowerCase();
		rows = rows.filter((t) => t.name.toLowerCase().includes(kw) || t.code.toLowerCase().includes(kw));
	}
	if (q.status !== undefined) rows = rows.filter((t) => t.status === q.status);
	return paginate(rows, q.pageNum, q.pageSize) as PageResult<DictType>;
}

export async function createDictType(data: Omit<DictType, 'id' | 'createTime'>): Promise<DictType> {
	await delay(200);
	if (typeStore.some((t) => t.code === data.code)) throw new Error('字典编码已存在');
	const t: DictType = { ...data, id: nextId(), createTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	typeStore.unshift(t);
	return t;
}

export async function updateDictType(id: number, data: Partial<DictType>): Promise<DictType> {
	await delay(200);
	const idx = typeStore.findIndex((t) => t.id === id);
	if (idx < 0) throw new Error('字典类型不存在');
	typeStore[idx] = { ...typeStore[idx], ...data, updateTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	return typeStore[idx];
}

export async function removeDictType(id: number): Promise<void> {
	await delay(200);
	const hasData = dataStore.some((d) => d.dictTypeId === id);
	if (hasData) throw new Error('字典下仍有字典数据,不可删除');
	const idx = typeStore.findIndex((t) => t.id === id);
	if (idx >= 0) typeStore.splice(idx, 1);
}

// ========== 字典数据 ==========

export interface DictDataQuery extends PageQuery {
	dictTypeId: number;
	status?: number;
}

export async function getDictDataList(q: DictDataQuery): Promise<PageResult<DictData>> {
	await delay(200);
	let rows = dataStore.filter((d) => d.dictTypeId === q.dictTypeId);
	if (q.keyword) {
		const kw = q.keyword.toLowerCase();
		rows = rows.filter((d) => d.label.toLowerCase().includes(kw) || d.value.toLowerCase().includes(kw));
	}
	if (q.status !== undefined) rows = rows.filter((d) => d.status === q.status);
	return paginate(rows, q.pageNum, q.pageSize) as PageResult<DictData>;
}

export async function getDictDataByTypeCode(code: string): Promise<DictData[]> {
	await delay(80);
	const t = typeStore.find((x) => x.code === code);
	if (!t) return [];
	return dataStore.filter((d) => d.dictTypeId === t.id);
}

export async function createDictData(data: Omit<DictData, 'id' | 'createTime'>): Promise<DictData> {
	await delay(200);
	const d: DictData = { ...data, id: nextId(), createTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	dataStore.unshift(d);
	return d;
}

export async function updateDictData(id: number, data: Partial<DictData>): Promise<DictData> {
	await delay(200);
	const idx = dataStore.findIndex((d) => d.id === id);
	if (idx < 0) throw new Error('字典数据不存在');
	dataStore[idx] = { ...dataStore[idx], ...data, updateTime: new Date().toISOString().slice(0, 19).replace('T', ' ') };
	return dataStore[idx];
}

export async function removeDictData(id: number): Promise<void> {
	await delay(200);
	const idx = dataStore.findIndex((d) => d.id === id);
	if (idx >= 0) dataStore.splice(idx, 1);
}