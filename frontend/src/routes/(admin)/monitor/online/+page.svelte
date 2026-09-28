<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/monitor';
	import type { OnlineUser } from '$lib/api/real/monitor';
	import { Button } from '$lib/components/ui/button/index.js';
	import { authStore } from '$lib/stores/auth.svelte';
	import { toast } from 'svelte-sonner';

	let rows = $state<OnlineUser[]>([]);
	let loading = $state(false);
	let error = $state<string | null>(null);
	const canKick = $derived(authStore.hasPermission('monitor:online:kick'));

	async function load() {
		loading = true;
		error = null;
		try {
			rows = await api.getOnlineUsers();
		} catch (e) {
			error = e instanceof Error ? e.message : '加载失败';
		} finally {
			loading = false;
		}
	}

	async function kickUser(row: OnlineUser) {
		if (!confirm(`强退 ${row.username} 的全部设备？`)) return;
		await api.kickOnlineUser(row.userId);
		toast.success('已强退');
		await load();
	}

	async function kickDevice(row: OnlineUser, sid: string) {
		await api.kickOnlineDevice(row.userId, sid);
		toast.success('已下线该设备');
		await load();
	}

	function when(ms: number) {
		return ms ? new Date(ms).toLocaleString('zh-CN') : '-';
	}

	onMount(load);
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex items-center justify-between">
		<div>
			<h2 class="text-lg font-semibold">在线用户</h2>
			<p class="text-muted-foreground text-sm">只统计本次进程启动后登录或刷新过的会话。不能强退自己。</p>
		</div>
		<Button variant="outline" size="sm" onclick={load} disabled={loading}>刷新</Button>
	</div>
	{#if error}<p class="text-destructive text-sm">{error}</p>{/if}
	{#if !rows.length && !loading}
		<p class="text-muted-foreground text-sm">当前没有在线用户。</p>
	{/if}
	{#each rows as row (row.userId)}
		<section class="rounded-lg border p-4">
			<div class="mb-3 flex items-center justify-between gap-3">
				<div>
					<p class="font-medium">{row.nickname || row.username} <span class="text-muted-foreground text-sm">{row.username}</span></p>
					<p class="text-muted-foreground text-xs">{row.deptName || '未分配部门'} · {row.deviceCount} 个设备</p>
				</div>
				{#if canKick && row.userId !== authStore.currentUser?.id}
					<Button variant="destructive" size="sm" onclick={() => kickUser(row)}>强退</Button>
				{/if}
			</div>
			<div class="flex flex-col gap-2">
				{#each row.devices as device (device.sid)}
					<div class="flex items-center justify-between gap-3 text-sm">
						<div class="min-w-0">
							<p class="truncate">{device.ip || '-'} · {when(device.loginAt)}</p>
							<p class="text-muted-foreground truncate text-xs">{device.ua || '-'}</p>
						</div>
						{#if canKick && row.userId !== authStore.currentUser?.id}
							<Button variant="ghost" size="sm" onclick={() => kickDevice(row, device.sid)}>下线</Button>
						{/if}
					</div>
				{/each}
			</div>
		</section>
	{/each}
</div>
