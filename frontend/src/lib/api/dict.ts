/**
 * 字典管理 API —— 根据 PUBLIC_USE_MOCK 自动切换 mock / 真实后端
 */
import { USE_MOCK } from '$lib/config';
import * as mock from './mock/dict';
import * as real from './real/dict';

export const getDictTypeList = USE_MOCK ? mock.getDictTypeList : real.getDictTypeList;
export const createDictType = USE_MOCK ? mock.createDictType : real.createDictType;
export const updateDictType = USE_MOCK ? mock.updateDictType : real.updateDictType;
export const removeDictType = USE_MOCK ? mock.removeDictType : real.removeDictType;
export const getDictDataList = USE_MOCK ? mock.getDictDataList : real.getDictDataList;
export const getDictDataByTypeCode = USE_MOCK ? mock.getDictDataByTypeCode : real.getDictDataByTypeCode;
export const createDictData = USE_MOCK ? mock.createDictData : real.createDictData;
export const updateDictData = USE_MOCK ? mock.updateDictData : real.updateDictData;
export const removeDictData = USE_MOCK ? mock.removeDictData : real.removeDictData;
