/**
 * 异常日志 API —— 根据 PUBLIC_USE_MOCK 自动切换 mock / 真实后端
 */
import { USE_MOCK } from '$lib/config';
import * as mock from './mock/error-log';
import * as real from './real/error-log';

export const getErrorLogList = USE_MOCK ? mock.getErrorLogList : real.getErrorLogList;
export const getErrorLogById = USE_MOCK ? mock.getErrorLogById : real.getErrorLogById;
export const cleanErrorLog = USE_MOCK ? mock.cleanErrorLog : real.cleanErrorLog;
