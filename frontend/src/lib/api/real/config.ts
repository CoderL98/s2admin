/**
 * 系统配置 API —— 真实后端实现
 */
import { get, post, put, del } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';
import type { SysConfig } from '$lib/mock/configs';

export interface ConfigQuery extends PageQuery {
	groupCode?: string;
	configType?: string;
}

export type ConfigPayload = Partial<Omit<SysConfig, 'id' | 'createTime'>>;

export async function getConfigList(q: ConfigQuery): Promise<PageResult<SysConfig>> {
	return get<PageResult<SysConfig>>('/api/system/config', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword,
		groupCode: q.groupCode,
		configType: q.configType
	});
}

export async function getConfigGroups(): Promise<string[]> {
	return get<string[]>('/api/system/config/groups');
}

export async function getConfigByKey(key: string): Promise<SysConfig | undefined> {
	try {
		return await get<SysConfig>('/api/system/config/key/' + key);
	} catch {
		return undefined;
	}
}

export async function createConfig(data: ConfigPayload): Promise<SysConfig> {
	return post<SysConfig>('/api/system/config', data);
}

export async function updateConfig(id: number, data: ConfigPayload): Promise<SysConfig> {
	return put<SysConfig>('/api/system/config/' + id, data);
}

export async function removeConfig(id: number): Promise<void> {
	return del<void>('/api/system/config/' + id);
}
