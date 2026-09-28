<script lang="ts">
	import { onMount } from 'svelte';
	import * as Card from '$lib/components/ui/card/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { authStore } from '$lib/stores/auth.svelte';
	import { USE_MOCK } from '$lib/config';
	import { getDashboardStats } from '$lib/api/dashboard';
	import * as loginLogApi from '$lib/api/login-log';
	import { getPublishedNotices } from '$lib/api/notice';
	import type { DashboardStats, LoginLog, Notice } from '$lib/types/entities';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { loginStatusBadge } from '$lib/components/badges';
	import UsersIcon from '@tabler/icons-svelte/icons/users';
	import ShieldIcon from '@tabler/icons-svelte/icons/shield';
	import LoginIcon from '@tabler/icons-svelte/icons/login';
	import AlertTriangleIcon from '@tabler/icons-svelte/icons/alert-triangle';

	let stats = $state<DashboardStats | null>(null);
	let recentLogs = $state<LoginLog[]>([]);
	let notices = $state<Notice[]>([]);
	let loadError = $state<string | null>(null);
	let popupNotice = $state<Notice | null>(null);

	function noticeSeenKey() {
		return `s2admin_notice_seen_${authStore.currentUser?.id ?? 'anon'}`;
	}

	function loadSeen(): number[] {
		try {
			const raw = localStorage.getItem(noticeSeenKey());
			return raw ? (JSON.parse(raw) as number[]) : [];
		} catch {
			return [];
		}
	}

	function markNoticeSeen(id: number) {
		const seen = new Set(loadSeen());
		seen.add(id);
		localStorage.setItem(noticeSeenKey(), JSON.stringify([...seen]));
	}

	function nextPopup(list: Notice[]) {
		const seen = new Set(loadSeen());
		return list.find((n) => n.pinned === 1 && !seen.has(n.id)) ?? null;
	}

	onMount(async () => {
		if (authStore.currentUser?.mustChangePassword) return;
		const [s, logs, n] = await Promise.all([
			getDashboardStats().catch((e) => {
				loadError = e instanceof Error ? e.message : '仪表盘数据加载失败';
				return null;
			}),
			loginLogApi.getLoginLogList({ pageNum: 1, pageSize: 5 }).catch(() => ({ records: [] as LoginLog[] })),
			getPublishedNotices().catch(() => [] as Notice[])
		]);
		stats = s;
		recentLogs = logs.records;
		notices = [...n].sort((a, b) => (b.pinned ?? 0) - (a.pinned ?? 0)).slice(0, 5);
		popupNotice = nextPopup(notices);
	});

	function dismissPopup() {
		if (!popupNotice) return;
		markNoticeSeen(popupNotice.id);
		popupNotice = nextPopup(notices);
	}

	function value(v: number | undefined) {
		return v == null ? '—' : v.toLocaleString();
	}

	const cards = $derived([
		{ title: '用户总数', value: stats?.userCount, hint: '系统中所有注册用户', icon: UsersIcon },
		{ title: '角色数量', value: stats?.roleCount, hint: 'RBAC 权限模型的角色', icon: ShieldIcon },
		{ title: '今日登录', value: stats?.todayLoginCount, hint: '今日累计登录记录', icon: LoginIcon },
		{ title: '异常日志', value: stats?.errorCount, hint: '系统累计异常记录', icon: AlertTriangleIcon }
	]);
</script>

<div class="flex flex-col gap-6 px-4 lg:px-6">
	{#each notices as n (n.id)}
		<div class="bg-primary/5 border-primary/20 rounded-md border px-3 py-2 text-sm">
			<p class="font-medium">{n.title}</p>
			{#if n.content}
				<p class="text-muted-foreground mt-1 line-clamp-2">{n.content}</p>
			{/if}
		</div>
	{/each}
	<Card.Root>
		<Card.Header>
			<Card.Title>欢迎回来,{authStore.currentUser?.nickname ?? '管理员'}</Card.Title>
			<Card.Description>
				S2Admin 通用后台管理系统骨架。当前数据来源:{USE_MOCK ? '本地 Mock' : 'Spring Boot 后端'}。
			</Card.Description>
		</Card.Header>
	</Card.Root>

	{#if loadError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{loadError}</div>
	{/if}

	<div class="grid grid-cols-1 gap-4 @xl/main:grid-cols-2 @5xl/main:grid-cols-4">
		{#each cards as card (card.title)}
			<Card.Root class="from-primary/5 to-card bg-gradient-to-t shadow-xs">
				<Card.Header>
					<div class="flex items-center justify-between">
						<Card.Description>{card.title}</Card.Description>
						<card.icon class="text-muted-foreground size-4" />
					</div>
					<Card.Title class="text-3xl tabular-nums">
						{#if stats === null && !loadError}
							<Skeleton class="h-9 w-20" />
						{:else}
							{value(card.value)}
						{/if}
					</Card.Title>
				</Card.Header>
				<Card.Footer>
					<p class="text-muted-foreground text-xs">{card.hint}</p>
				</Card.Footer>
			</Card.Root>
		{/each}
	</div>

	<Card.Root>
		<Card.Header>
			<Card.Title>最近登录</Card.Title>
			<Card.Description>最近 5 条登录记录</Card.Description>
		</Card.Header>
		<Card.Content>
			{#if recentLogs.length === 0}
				<p class="text-muted-foreground text-sm">暂无登录记录</p>
			{:else}
				<div class="divide-y">
					{#each recentLogs as log (log.id)}
						{@const status = loginStatusBadge(log.status)}
						<div class="flex flex-wrap items-center justify-between gap-2 py-3 text-sm">
							<div>
								<p class="font-medium">{log.username}</p>
								<p class="text-muted-foreground text-xs">{log.ip} · {log.browser} · {log.os}</p>
							</div>
							<div class="flex items-center gap-3">
								{#if status.kind === 'badge'}
									<span
										class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium {status.tone ===
										'success'
											? 'bg-emerald-100 text-emerald-800'
											: 'bg-red-100 text-red-700'}"
									>
										{status.text}
									</span>
								{/if}
								<span class="text-muted-foreground text-xs">{log.loginTime}</span>
							</div>
						</div>
					{/each}
				</div>
			{/if}
		</Card.Content>
	</Card.Root>
</div>

<Dialog.Root
	open={popupNotice !== null}
	onOpenChange={(open) => {
		if (!open) dismissPopup();
	}}
>
	<Dialog.Content class="sm:max-w-lg">
		<Dialog.Header>
			<Dialog.Title>{popupNotice?.title ?? '公告'}</Dialog.Title>
			<Dialog.Description>置顶公告</Dialog.Description>
		</Dialog.Header>
		{#if popupNotice?.content}
			<div class="text-muted-foreground max-h-64 overflow-auto whitespace-pre-wrap text-sm">{popupNotice.content}</div>
		{/if}
		<Dialog.Footer>
			<Button onclick={dismissPopup}>知道了</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
