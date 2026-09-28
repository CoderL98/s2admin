/**
 * 系统配置 API —— 根据 PUBLIC_USE_MOCK 自动切换 mock / 真实后端
 */
import { USE_MOCK } from '$lib/config';
import * as mock from './mock/config';
import * as real from './real/config';

export const getConfigList = USE_MOCK ? mock.getConfigList : real.getConfigList;
export const getConfigGroups = USE_MOCK ? mock.getConfigGroups : real.getConfigGroups;
export const getConfigByKey = USE_MOCK ? mock.getConfigByKey : real.getConfigByKey;
export const createConfig = USE_MOCK ? mock.createConfig : real.createConfig;
export const updateConfig = USE_MOCK ? mock.updateConfig : real.updateConfig;
export const removeConfig = USE_MOCK ? mock.removeConfig : real.removeConfig;
