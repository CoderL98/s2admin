import type { PageQuery, PageResult } from '$lib/types/auth';
import type { JobLog, OnlineUser, ServerInfo, SysJob } from '../real/monitor';

const empty = <T>(q: PageQuery): PageResult<T> => ({
	records: [],
	total: 0,
	size: q.pageSize ?? 10,
	current: q.pageNum ?? 1,
	pages: 0
});

export async function getOnlineUsers(): Promise<OnlineUser[]> {
	return [];
}
export async function kickOnlineUser(_userId: number) {}
export async function kickOnlineDevice(_userId: number, _sid: string) {}
export async function getServerInfo(): Promise<ServerInfo> {
	return {
		osName: 'mock',
		osArch: 'x64',
		javaVersion: '0',
		jvmName: 'mock',
		processors: 1,
		startTime: '',
		uptimeSeconds: 0,
		threadCount: 1,
		heapUsed: 0,
		heapMax: 1,
		nonHeapUsed: 0,
		diskTotal: 1,
		diskFree: 1,
		workdir: '',
		systemLoad: 0
	};
}
export async function getJobs(q: PageQuery) {
	return empty<SysJob>(q);
}
export async function getJobLogs(_jobId: number | undefined, q: PageQuery) {
	return empty<JobLog>(q);
}
export async function saveJob(data: Partial<SysJob> & { name: string; code: string; cron: string; handler: string }, id?: number) {
	return { id: id ?? 1, status: 1, ...data } as SysJob;
}
export async function removeJob(_id: number) {}
export async function runJob(_id: number) {}
