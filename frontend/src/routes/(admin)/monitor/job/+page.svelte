<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/monitor';
	import type { JobLog, SysJob } from '$lib/api/real/monitor';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { badge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import { toast } from 'svelte-sonner';

	let rows = $state<SysJob[]>([]);
	let logs = $state<JobLog[]>([]);
	let loading = $state(false);
	let error = $state<string | null>(null);
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);
	let search = $state('');
	let selectedId = $state<number | undefined>(undefined);

	let dialogOpen = $state(false);
	let editing = $state<Partial<SysJob> | null>(null);
	let formError = $state<string | null>(null);
	let saving = $state(false);
	const canEdit = $derived(authStore.hasPermission('monitor:job:edit'));

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await api.getJobs({ pageNum, pageSize, keyword: search || undefined });
			rows = res.records;
			total = res.total;
			logs = (await api.getJobLogs(selectedId, { pageNum: 1, pageSize: 8 })).records;
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

	function openAdd() {
		editing = { status: 1, handler: 'sample.ping', cron: '0 */10 * * * *', code: '', name: '' };
		formError = null;
		dialogOpen = true;
	}

	function openEdit(row: SysJob) {
		editing = { ...row };
		formError = null;
		dialogOpen = true;
	}

	async function save() {
		if (!editing?.name?.trim() || !editing.code?.trim() || !editing.cron?.trim()) {
			formError = '名称、编码和 cron 不能为空';
			throw new Error(formError);
		}
		saving = true;
		formError = null;
		try {
			await api.saveJob(
				{
					name: editing.name.trim(),
					code: editing.code.trim(),
					cron: editing.cron.trim(),
					handler: editing.handler || 'sample.ping',
					params: editing.params,
					status: editing.status ?? 1,
					remark: editing.remark
				},
				editing.id
			);
			toast.success('已保存');
			await load();
		} catch (e) {
			formError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			saving = false;
		}
	}

	async function run(row: SysJob) {
		await api.runJob(row.id);
		toast.success('已执行');
		selectedId = row.id;
		await load();
	}

	const columns: CrudColumn<SysJob>[] = [
		{ header: '名称', accessor: (r) => r.name },
		{ header: '编码', accessor: (r) => r.code, cell: (r) => ({ kind: 'code', text: r.code }) },
		{ header: 'cron', accessor: (r) => r.cron, class: 'w-40' },
		{ header: '处理器', accessor: (r) => r.handler, class: 'w-32' },
		{
			header: '状态',
			accessor: (r) => r.status,
			cell: (r) => (r.status === 0 ? badge('启用', 'success') : badge('停用', 'outline')),
			class: 'w-20'
		},
		{ header: '下次执行', accessor: (r) => r.nextFireTime ?? '-', class: 'w-44' }
	];

	onMount(load);
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div>
		<h2 class="text-lg font-semibold">定时任务</h2>
		<p class="text-muted-foreground text-sm">6 位 cron（含秒），两次触发至少间隔 30 秒。处理器只有 sample.ping 和 log.cleanup（参数为保留天数）。</p>
	</div>
	<CrudTable
		data={rows}
		{columns}
		{loading}
		{error}
		searchValue={search}
		onSearchChange={onSearch}
		searchPlaceholder="搜索任务名称或编码"
		addLabel="新增任务"
		onAdd={canEdit ? openAdd : undefined}
		onEdit={canEdit ? openEdit : undefined}
		onDelete={canEdit ? async (row) => { await api.removeJob(row.id); toast.success('已删除'); await load(); } : undefined}
		extraActions={canEdit ? [{ label: '执行一次', onClick: run }, { label: '日志', onClick: async (row) => { selectedId = row.id; await load(); } }] : [{ label: '日志', onClick: async (row) => { selectedId = row.id; await load(); } }]}
		{pageNum}
		{pageSize}
		{total}
		onPageChange={(p, s) => { pageNum = p; pageSize = s; load(); }}
	/>
	<section class="rounded-lg border p-4">
		<h3 class="mb-2 text-sm font-medium">最近执行{selectedId ? ` · 任务 ${selectedId}` : ''}</h3>
		{#if !logs.length}
			<p class="text-muted-foreground text-sm">还没有执行记录。</p>
		{:else}
			<ul class="flex flex-col gap-2 text-sm">
				{#each logs as log (log.id)}
					<li class="flex justify-between gap-3">
						<span>{log.fireTime} · {log.jobName} · {log.status === 0 ? '成功' : '失败'} · {log.costMs ?? 0} ms</span>
						<span class="text-muted-foreground truncate">{log.message}</span>
					</li>
				{/each}
			</ul>
		{/if}
	</section>
</div>

<FormDialog bind:open={dialogOpen} title={editing?.id ? '编辑任务' : '新增任务'} loading={saving} onSubmit={save}>
	{#if formError}<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{formError}</div>{/if}
	{#if editing}
		<FieldGroup>
			<div class="grid grid-cols-2 gap-3">
				<Field><FieldLabel>名称</FieldLabel><Input bind:value={editing.name} /></Field>
				<Field><FieldLabel>编码</FieldLabel><Input bind:value={editing.code} placeholder="LOG_CLEANUP" /></Field>
				<Field><FieldLabel>cron</FieldLabel><Input bind:value={editing.cron} placeholder="0 0 3 * * *" /></Field>
				<Field>
					<FieldLabel>处理器</FieldLabel>
					<Select.Root type="single" value={editing.handler} onValueChange={(v) => (editing!.handler = v)}>
						<Select.Trigger>{editing.handler}</Select.Trigger>
						<Select.Content>
							<Select.Item value="sample.ping">sample.ping</Select.Item>
							<Select.Item value="log.cleanup">log.cleanup</Select.Item>
						</Select.Content>
					</Select.Root>
				</Field>
				<Field><FieldLabel>参数</FieldLabel><Input bind:value={editing.params} placeholder="清理天数，如 90" /></Field>
				<Field>
					<FieldLabel>状态</FieldLabel>
					<Select.Root type="single" value={String(editing.status ?? 1)} onValueChange={(v) => (editing!.status = Number(v))}>
						<Select.Trigger>{editing.status === 0 ? '启用' : '停用'}</Select.Trigger>
						<Select.Content>
							<Select.Item value="0">启用</Select.Item>
							<Select.Item value="1">停用</Select.Item>
						</Select.Content>
					</Select.Root>
				</Field>
			</div>
			<Field><FieldLabel>说明</FieldLabel><Input bind:value={editing.remark} /></Field>
		</FieldGroup>
	{/if}
</FormDialog>
