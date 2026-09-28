<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/login-log';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import DictSelect from '$lib/components/dict-select.svelte';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { loginStatusBadge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import { dayRange } from '$lib/utils/datetime';
	import type { LoginLog } from '$lib/types/entities';
	import TrashIcon from '@tabler/icons-svelte/icons/trash';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<LoginLog[]>([]);
	let search = $state('');
	let statusFilter = $state('');
	let beginDate = $state('');
	let endDate = $state('');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);

	const canDelete = $derived(authStore.hasPermission('monitor:log:delete'));
	const canView = $derived(authStore.hasPermission('monitor:log:view'));

	function query() {
		const range = dayRange(beginDate, endDate);
		return {
			pageNum,
			pageSize,
			keyword: search || undefined,
			status: statusFilter ? (Number(statusFilter) as LoginLog['status']) : undefined,
			beginTime: range.beginTime,
			endTime: range.endTime
		};
	}

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await api.getLoginLogList(query());
			rows = res.records;
			total = res.total;
		} catch (e) {
			error = e instanceof Error ? e.message : '加载失败';
		} finally {
			loading = false;
		}
	}

	let timer: ReturnType<typeof setTimeout>;
	function onSearch(v: string) {
		search = v;
		clearTimeout(timer);
		timer = setTimeout(() => {
			pageNum = 1;
			load();
		}, 200);
	}

	onMount(load);

	async function clean() {
		if (!confirm('确认清空全部登录日志?该操作不可恢复。')) return;
		await api.cleanLoginLog();
		toast.success('登录日志已清空');
		pageNum = 1;
		await load();
	}

	async function doExport() {
		try {
			await api.exportLoginLog(query());
			toast.success('已开始下载');
		} catch (e) {
			toast.error(e instanceof Error ? e.message : '导出失败');
		}
	}

	const columns: CrudColumn<LoginLog>[] = [
		{ header: 'ID', accessor: (r) => r.id, class: 'w-16' },
		{ header: '用户名', accessor: (r) => r.username, class: 'w-32' },
		{ header: 'IP', accessor: (r) => r.ip, class: 'w-32' },
		{ header: '登录地', accessor: (r) => r.location, class: 'w-32' },
		{ header: '浏览器', accessor: (r) => r.browser, class: 'w-32' },
		{ header: '操作系统', accessor: (r) => r.os, class: 'w-32' },
		{ header: '状态', accessor: (r) => r.status, cell: (r) => loginStatusBadge(r.status), class: 'w-20' },
		{ header: '消息', accessor: (r) => r.message },
		{ header: '登录时间', accessor: (r) => r.loginTime, class: 'w-40' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">登录日志</h2>
			<p class="text-muted-foreground text-sm">查看系统登录记录与异常登录</p>
		</div>
		<div class="flex flex-wrap items-center gap-2">
			<DictSelect
				typeCode="login_status"
				bind:value={statusFilter}
				allowEmpty
				emptyLabel="全部状态"
				class="w-28"
				onValueChange={() => {
					pageNum = 1;
					load();
				}}
			/>
			<Input
				type="date"
				class="w-36"
				bind:value={beginDate}
				onchange={() => {
					pageNum = 1;
					load();
				}}
			/>
			<Input
				type="date"
				class="w-36"
				bind:value={endDate}
				onchange={() => {
					pageNum = 1;
					load();
				}}
			/>
			{#if canView}
				<Button variant="outline" size="sm" onclick={doExport}>导出</Button>
			{/if}
			{#if canDelete}
				<Button variant="outline" size="sm" onclick={clean}>
					<TrashIcon />
					<span>清空</span>
				</Button>
			{/if}
		</div>
	</div>

	<CrudTable
		data={rows}
		{columns}
		{loading}
		{error}
		searchValue={search}
		onSearchChange={onSearch}
		searchPlaceholder="搜索用户名或 IP"
		hideActions
		{pageNum}
		{pageSize}
		{total}
		onPageChange={(p, s) => {
			pageNum = p;
			pageSize = s;
			load();
		}}
	/>
</div>
