/**
 * 登录日志 API —— 根据 PUBLIC_USE_MOCK 自动切换 mock / 真实后端
 */
import { USE_MOCK } from '$lib/config';
import * as mock from './mock/login-log';
import * as real from './real/login-log';

export const getLoginLogList = USE_MOCK ? mock.getLoginLogList : real.getLoginLogList;
export const cleanLoginLog = USE_MOCK ? mock.cleanLoginLog : real.cleanLoginLog;
export const exportLoginLog = USE_MOCK ? mock.exportLoginLog : real.exportLoginLog;
