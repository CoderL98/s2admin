import { USE_MOCK } from '$lib/config';
import * as mock from './mock/monitor';
import * as real from './real/monitor';

export const getOnlineUsers = USE_MOCK ? mock.getOnlineUsers : real.getOnlineUsers;
export const kickOnlineUser = USE_MOCK ? mock.kickOnlineUser : real.kickOnlineUser;
export const kickOnlineDevice = USE_MOCK ? mock.kickOnlineDevice : real.kickOnlineDevice;
export const getServerInfo = USE_MOCK ? mock.getServerInfo : real.getServerInfo;
export const getJobs = USE_MOCK ? mock.getJobs : real.getJobs;
export const getJobLogs = USE_MOCK ? mock.getJobLogs : real.getJobLogs;
export const saveJob = USE_MOCK ? mock.saveJob : real.saveJob;
export const removeJob = USE_MOCK ? mock.removeJob : real.removeJob;
export const runJob = USE_MOCK ? mock.runJob : real.runJob;
