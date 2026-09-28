/**
 * 操作日志 API —— 根据 PUBLIC_USE_MOCK 自动切换 mock / 真实后端
 */
import { USE_MOCK } from '$lib/config';
import * as mock from './mock/op-log';
import * as real from './real/op-log';

export const getOpLogList = USE_MOCK ? mock.getOpLogList : real.getOpLogList;
export const getOpLogById = USE_MOCK ? mock.getOpLogById : real.getOpLogById;
export const cleanOpLog = USE_MOCK ? mock.cleanOpLog : real.cleanOpLog;
export const exportOpLog = USE_MOCK ? mock.exportOpLog : real.exportOpLog;
