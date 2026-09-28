<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/error-log';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import { dayRange } from '$lib/utils/datetime';
	import type { ErrorLog } from '$lib/types/entities';
	import TrashIcon from '@tabler/icons-svelte/icons/trash';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<ErrorLog[]>([]);
	let search = $state('');
	let beginDate = $state('');
	let endDate = $state('');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);
	let detail = $state<ErrorLog | null>(null);
	const canDelete = $derived(authStore.hasPermission('monitor:log:delete'));
	const canView = $derived(authStore.hasPermission('monitor:log:view'));

	async function load() {
		loading = true;
		error = null;
		try {
			const range = dayRange(beginDate, endDate);
			const res = await api.getErrorLogList({
				pageNum,
				pageSize,
				keyword: search || undefined,
				beginTime: range.beginTime,
				endTime: range.endTime
			});
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

	async function openDetail(r: ErrorLog) {
		try {
			detail = (await api.getErrorLogById(r.id)) ?? r;
		} catch {
			detail = r;
		}
	}

	async function clean() {
		if (!confirm('确认清空全部异常日志?该操作不可恢复。')) return;
		await api.cleanErrorLog();
		toast.success('异常日志已清空');
		pageNum = 1;
		await load();
	}

	const columns: CrudColumn<ErrorLog>[] = [
		{ header: 'ID', accessor: (r) => r.id, class: 'w-16' },
		{ header: '追踪 ID', accessor: (r) => r.traceId, cell: (r) => ({ kind: 'code', text: r.traceId }) },
		{ header: '用户', accessor: (r) => r.username ?? '-', class: 'w-24' },
		{ header: 'IP', accessor: (r) => r.ip, class: 'w-32' },
		{ header: '请求', accessor: (r) => r.method },
		{ header: 'URL', accessor: (r) => r.url, cell: (r) => ({ kind: 'code', text: r.url }) },
		{ header: '异常', accessor: (r) => r.exception.split(':')[1] ?? r.exception },
		{ header: '时间', accessor: (r) => r.errorTime, class: 'w-40' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">异常日志</h2>
			<p class="text-muted-foreground text-sm">查看系统运行时异常堆栈信息</p>
		</div>
		<div class="flex flex-wrap items-center gap-2">
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
		searchPlaceholder="搜索追踪 ID / 用户 / URL / 异常"
		extraActions={canView ? [{ label: '查看详情', onClick: openDetail }] : []}
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
			<Dialog.Title>异常详情</Dialog.Title>
			<Dialog.Description>{detail?.traceId ?? ''}</Dialog.Description>
		</Dialog.Header>
		{#if detail}
			<div class="grid gap-3 text-sm">
				<div class="grid grid-cols-2 gap-3">
					<div>
						<div class="text-muted-foreground">用户</div>
						<div>{detail.username ?? '-'}</div>
					</div>
					<div>
						<div class="text-muted-foreground">IP</div>
						<div>{detail.ip}</div>
					</div>
					<div>
						<div class="text-muted-foreground">请求方法</div>
						<div class="font-mono text-xs">{detail.method}</div>
					</div>
					<div>
						<div class="text-muted-foreground">URL</div>
						<div class="font-mono text-xs">{detail.url}</div>
					</div>
					<div class="col-span-2">
						<div class="text-muted-foreground">请求参数</div>
						<pre class="bg-muted mt-1 max-h-32 overflow-auto rounded p-2 text-xs">{detail.params ?? '(无)'}</pre>
					</div>
					<div class="col-span-2">
						<div class="text-muted-foreground">异常信息</div>
						<div class="text-destructive mt-1 font-mono text-xs">{detail.exception}</div>
					</div>
					<div class="col-span-2">
						<div class="text-muted-foreground">堆栈</div>
						<pre class="bg-muted mt-1 max-h-64 overflow-auto rounded p-2 text-xs">{detail.stackTrace}</pre>
					</div>
				</div>
			</div>
		{/if}
		<Dialog.Footer>
			<Button variant="outline" onclick={() => (detail = null)}>关闭</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
