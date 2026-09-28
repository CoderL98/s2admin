import { del, get, post, put } from '$lib/utils/request';
import type { PageQuery, PageResult } from '$lib/types/auth';

export interface OnlineDevice {
	sid: string;
	ip: string;
	ua: string;
	loginAt: number;
}

export interface OnlineUser {
	userId: number;
	username: string;
	nickname?: string;
	deptName?: string;
	deviceCount: number;
	devices: OnlineDevice[];
}

export interface ServerInfo {
	osName: string;
	osArch: string;
	javaVersion: string;
	jvmName: string;
	processors: number;
	startTime: string;
	uptimeSeconds: number;
	threadCount: number;
	heapUsed: number;
	heapMax: number;
	nonHeapUsed: number;
	diskTotal: number;
	diskFree: number;
	workdir: string;
	systemLoad: number;
}

export interface SysJob {
	id: number;
	name: string;
	code: string;
	cron: string;
	handler: string;
	params?: string;
	status: number;
	nextFireTime?: string;
	lastFireTime?: string;
	remark?: string;
}

export interface JobLog {
	id: number;
	jobId?: number;
	jobName?: string;
	status: number;
	message?: string;
	costMs?: number;
	fireTime?: string;
}

export async function getOnlineUsers(): Promise<OnlineUser[]> {
	return get<OnlineUser[]>('/api/monitor/online');
}

export async function kickOnlineUser(userId: number): Promise<void> {
	return del<void>('/api/monitor/online/' + userId);
}

export async function kickOnlineDevice(userId: number, sid: string): Promise<void> {
	return del<void>('/api/monitor/online/' + userId + '/' + encodeURIComponent(sid));
}

export async function getServerInfo(): Promise<ServerInfo> {
	return get<ServerInfo>('/api/monitor/server');
}

export async function getJobs(q: PageQuery): Promise<PageResult<SysJob>> {
	return get<PageResult<SysJob>>('/api/monitor/job', {
		pageNum: q.pageNum,
		pageSize: q.pageSize,
		keyword: q.keyword
	});
}

export async function getJobLogs(jobId: number | undefined, q: PageQuery): Promise<PageResult<JobLog>> {
	return get<PageResult<JobLog>>('/api/monitor/job/log', {
		jobId,
		pageNum: q.pageNum,
		pageSize: q.pageSize
	});
}

export async function saveJob(data: Partial<SysJob> & { name: string; code: string; cron: string; handler: string }, id?: number) {
	if (id) return put<SysJob>('/api/monitor/job/' + id, data);
	return post<SysJob>('/api/monitor/job', data);
}

export async function removeJob(id: number): Promise<void> {
	return del<void>('/api/monitor/job/' + id);
}

export async function runJob(id: number): Promise<void> {
	return post<void>('/api/monitor/job/' + id + '/run');
}
