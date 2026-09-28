/**
 * 字典管理 API —— 真实后端实现
 */
import { get, post, put, del } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { DictType } from '$lib/mock/dict-types';
import type { DictData } from '$lib/mock/dict-data';

// ========== 字典类型 ==========

export interface DictTypeQuery extends PageQuery {
	status?: number;
}

export type DictTypePayload = Partial<Omit<DictType, 'id' | 'createTime'>>;

export async function getDictTypeList(q: DictTypeQuery): Promise<PageResult<DictType>> {
	return get<PageResult<DictType>>('/api/system/dict/type', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		status: q.status
	});
}

export async function createDictType(data: DictTypePayload): Promise<DictType> {
	return post<DictType>('/api/system/dict/type', data);
}

export async function updateDictType(id: number, data: DictTypePayload): Promise<DictType> {
	return put<DictType>('/api/system/dict/type/' + id, data);
}

export async function removeDictType(id: number): Promise<void> {
	return del<void>('/api/system/dict/type/' + id);
}

// ========== 字典数据 ==========

export interface DictDataQuery extends PageQuery {
	dictTypeId: number;
	status?: number;
}

export type DictDataPayload = Partial<Omit<DictData, 'id' | 'createTime'>>;

export async function getDictDataList(q: DictDataQuery): Promise<PageResult<DictData>> {
	return get<PageResult<DictData>>('/api/system/dict/data', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		dictTypeId: q.dictTypeId,
		keyword: q.keyword,
		status: q.status
	});
}

export async function getDictDataByTypeCode(code: string): Promise<DictData[]> {
	return get<DictData[]>('/api/system/dict/data/type/' + code);
}

export async function createDictData(data: DictDataPayload): Promise<DictData> {
	return post<DictData>('/api/system/dict/data', data);
}

export async function updateDictData(id: number, data: DictDataPayload): Promise<DictData> {
	return put<DictData>('/api/system/dict/data/' + id, data);
}

export async function removeDictData(id: number): Promise<void> {
	return del<void>('/api/system/dict/data/' + id);
}
