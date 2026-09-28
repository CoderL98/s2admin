<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/op-log';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { badge, opStatusBadge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import { dayRange } from '$lib/utils/datetime';
	import type { OperationLog } from '$lib/types/entities';
	import TrashIcon from '@tabler/icons-svelte/icons/trash';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<OperationLog[]>([]);
	let search = $state('');
	let statusFilter = $state('');
	let beginDate = $state('');
	let endDate = $state('');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);
	let detail = $state<OperationLog | null>(null);

	const canDelete = $derived(authStore.hasPermission('monitor:log:delete'));
	const canView = $derived(authStore.hasPermission('monitor:log:view'));

	function query() {
		const range = dayRange(beginDate, endDate);
		return {
			pageNum,
			pageSize,
			keyword: search || undefined,
			status:
				statusFilter && statusFilter !== '__all__'
					? (Number(statusFilter) as OperationLog['status'])
					: undefined,
			beginTime: range.beginTime,
			endTime: range.endTime
		};
	}

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await api.getOpLogList(query());
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

	async function openDetail(r: OperationLog) {
		try {
			detail = (await api.getOpLogById(r.id)) ?? r;
		} catch {
			detail = r;
		}
	}

	async function clean() {
		if (!confirm('确认清空全部操作日志?该操作不可恢复。')) return;
		await api.cleanOpLog();
		toast.success('操作日志已清空');
		pageNum = 1;
		await load();
	}

	async function doExport() {
		try {
			await api.exportOpLog(query());
			toast.success('已开始下载');
		} catch (e) {
			toast.error(e instanceof Error ? e.message : '导出失败');
		}
	}

	function duration(ms: number): string {
		if (ms < 1000) return `${ms}ms`;
		return `${(ms / 1000).toFixed(2)}s`;
	}

	const columns: CrudColumn<OperationLog>[] = [
		{ header: 'ID', accessor: (r) => r.id, class: 'w-16' },
		{ header: '用户', accessor: (r) => r.username, class: 'w-24' },
		{ header: '模块', accessor: (r) => r.module, class: 'w-24' },
		{ header: '操作', accessor: (r) => r.operation, cell: (r) => badge(r.operation, 'info'), class: 'w-20' },
		{ header: '请求方法', accessor: (r) => r.method },
		{ header: '请求路径', accessor: (r) => r.url, cell: (r) => ({ kind: 'code', text: r.url }) },
		{ header: 'IP', accessor: (r) => r.ip, class: 'w-32' },
		{ header: '耗时', accessor: (r) => duration(r.executeTime), class: 'w-20', align: 'right' },
		{ header: '状态', accessor: (r) => r.status, cell: (r) => opStatusBadge(r.status), class: 'w-20' },
		{ header: '时间', accessor: (r) => r.operationTime, class: 'w-40' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">操作日志</h2>
			<p class="text-muted-foreground text-sm">记录用户对系统各项功能的操作</p>
		</div>
		<div class="flex flex-wrap items-center gap-2">
			<Select.Root
				type="single"
				bind:value={statusFilter}
				onValueChange={() => {
					pageNum = 1;
					load();
				}}
			>
				<Select.Trigger class="w-28">
					{!statusFilter || statusFilter === '__all__' ? '全部状态' : statusFilter === '0' ? '成功' : '失败'}
				</Select.Trigger>
				<Select.Content>
					<Select.Item value="__all__">全部状态</Select.Item>
					<Select.Item value="0">成功</Select.Item>
					<Select.Item value="1">失败</Select.Item>
				</Select.Content>
			</Select.Root>
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
		searchPlaceholder="搜索用户/模块/路径"
		extraActions={[{ label: '查看详情', onClick: openDetail }]}
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

<Dialog.Root
	open={detail !== null}
	onOpenChange={(o) => {
		if (!o) detail = null;
	}}
>
	<Dialog.Content class="sm:max-w-[720px]">
		<Dialog.Header>
			<Dialog.Title>操作详情</Dialog.Title>
			<Dialog.Description>{detail?.module ?? ''} / {detail?.operation ?? ''}</Dialog.Description>
		</Dialog.Header>
		{#if detail}
			<div class="grid gap-3 text-sm">
				<div class="grid grid-cols-2 gap-3">
					<div>
						<div class="text-muted-foreground">用户</div>
						<div>{detail.username}</div>
					</div>
					<div>
						<div class="text-muted-foreground">IP</div>
						<div>{detail.ip}</div>
					</div>
					<div>
						<div class="text-muted-foreground">请求</div>
						<div class="font-mono text-xs">{detail.method} {detail.url}</div>
					</div>
					<div>
						<div class="text-muted-foreground">耗时</div>
						<div>{duration(detail.executeTime)}</div>
					</div>
					<div class="col-span-2">
						<div class="text-muted-foreground">旧值</div>
						<pre class="bg-muted mt-1 max-h-32 overflow-auto rounded p-2 text-xs">{detail.oldValue ?? '(无)'}</pre>
					</div>
					<div class="col-span-2">
						<div class="text-muted-foreground">新值</div>
						<pre class="bg-muted mt-1 max-h-32 overflow-auto rounded p-2 text-xs">{detail.newValue ?? '(无)'}</pre>
					</div>
					{#if detail.errorMsg}
						<div class="col-span-2">
							<div class="text-muted-foreground">错误</div>
							<div class="text-destructive mt-1 font-mono text-xs">{detail.errorMsg}</div>
						</div>
					{/if}
				</div>
			</div>
		{/if}
		<Dialog.Footer>
			<Button variant="outline" onclick={() => (detail = null)}>关闭</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
