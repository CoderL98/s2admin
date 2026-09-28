<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/monitor';
	import type { ServerInfo } from '$lib/api/real/monitor';
	import { Button } from '$lib/components/ui/button/index.js';

	let info = $state<ServerInfo | null>(null);
	let error = $state<string | null>(null);

	function mb(n: number) {
		if (!n || n < 0) return '-';
		return (n / 1024 / 1024).toFixed(1) + ' MB';
	}

	function duration(seconds: number) {
		const h = Math.floor(seconds / 3600);
		const m = Math.floor((seconds % 3600) / 60);
		return `${h} 小时 ${m} 分`;
	}

	async function load() {
		error = null;
		try {
			info = await api.getServerInfo();
		} catch (e) {
			error = e instanceof Error ? e.message : '加载失败';
		}
	}

	onMount(load);
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex items-center justify-between">
		<div>
			<h2 class="text-lg font-semibold">服务监控</h2>
			<p class="text-muted-foreground text-sm">当前进程的运行环境和资源占用，不依赖额外监控组件。</p>
		</div>
		<Button variant="outline" size="sm" onclick={load}>刷新</Button>
	</div>
	{#if error}<p class="text-destructive text-sm">{error}</p>{/if}
	{#if info}
		<dl class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
			{#each [
				['操作系统', `${info.osName} / ${info.osArch}`],
				['Java', `${info.javaVersion}`],
				['JVM', info.jvmName],
				['CPU 核数', String(info.processors)],
				['系统负载', info.systemLoad < 0 ? '不可用' : String(info.systemLoad)],
				['线程数', String(info.threadCount)],
				['启动时间', info.startTime],
				['已运行', duration(info.uptimeSeconds)],
				['堆内存', `${mb(info.heapUsed)} / ${mb(info.heapMax)}`],
				['非堆内存', mb(info.nonHeapUsed)],
				['磁盘可用', `${mb(info.diskFree)} / ${mb(info.diskTotal)}`],
				['工作目录', info.workdir]
			] as item (item[0])}
				<div class="rounded-lg border p-3">
					<dt class="text-muted-foreground text-xs">{item[0]}</dt>
					<dd class="mt-1 break-all text-sm">{item[1]}</dd>
				</div>
			{/each}
		</dl>
	{/if}
</div>
