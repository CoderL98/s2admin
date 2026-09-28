<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/notice';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { badge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { Notice } from '$lib/types/entities';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<Notice[]>([]);
	let search = $state('');
	let searchTimer: ReturnType<typeof setTimeout>;
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);
	let dialogOpen = $state(false);
	let editing = $state<Partial<Notice> | null>(null);
	let formError = $state<string | null>(null);

	const canAdd = $derived(authStore.hasPermission('system:notice:add'));
	const canEdit = $derived(authStore.hasPermission('system:notice:edit'));
	const canDelete = $derived(authStore.hasPermission('system:notice:delete'));

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await api.getNoticeList({ pageNum, pageSize, keyword: search || undefined });
			rows = res.records;
			total = res.total;
		} catch (e) {
			error = e instanceof Error ? e.message : '加载失败';
		} finally {
			loading = false;
		}
	}

	onMount(load);

	function openAdd() {
		editing = { type: 1, status: 0, pinned: 0, content: '' };
		formError = null;
		dialogOpen = true;
	}

	function openEdit(n: Notice) {
		editing = { ...n };
		formError = null;
		dialogOpen = true;
	}

	async function save() {
		if (!editing?.title?.trim()) {
			formError = '标题不能为空';
			throw new Error(formError);
		}
		saving = true;
		try {
			const payload = {
				title: editing.title,
				content: editing.content,
				type: (editing.type ?? 1) as Notice['type'],
				status: (editing.status ?? 0) as Notice['status'],
				pinned: (editing.pinned ?? 0) as Notice['pinned'],
				remark: editing.remark
			};
			if (editing.id) await api.updateNotice(editing.id, payload);
			else await api.createNotice(payload);
			toast.success('公告已保存');
			await load();
		} catch (e) {
			formError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			saving = false;
		}
	}

	async function togglePublish(n: Notice) {
		await api.publishNotice(n.id, n.status !== 1);
		toast.success(n.status === 1 ? '已撤回' : '已发布');
		await load();
	}

	async function remove(n: Notice) {
		await api.removeNotice(n.id);
		toast.success('已删除');
		await load();
	}

	const columns: CrudColumn<Notice>[] = [
		{ header: '标题', accessor: (r) => r.title },
		{
			header: '类型',
			accessor: (r) => r.type,
			cell: (r) => badge(r.type === 2 ? '公告' : '通知', 'info'),
			class: 'w-20'
		},
		{
			header: '置顶',
			accessor: (r) => r.pinned,
			cell: (r) => (r.pinned === 1 ? badge('是', 'warning') : '否'),
			class: 'w-16'
		},
		{
			header: '状态',
			accessor: (r) => r.status,
			cell: (r) => (r.status === 1 ? badge('已发布', 'success') : badge('草稿', 'outline')),
			class: 'w-20'
		},
		{ header: '发布时间', accessor: (r) => r.publishTime ?? '-', class: 'w-40' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex items-center justify-between">
		<div>
			<h2 class="text-lg font-semibold">公告管理</h2>
			<p class="text-muted-foreground text-sm">发布系统通知与公告</p>
		</div>
	</div>
	<CrudTable
		data={rows}
		{columns}
		{loading}
		{error}
		searchValue={search}
		onSearchChange={(v) => {
			search = v;
			pageNum = 1;
			clearTimeout(searchTimer);
			searchTimer = setTimeout(() => load(), 200);
		}}
		searchPlaceholder="搜索标题"
		addLabel="新增公告"
		onAdd={canAdd ? openAdd : undefined}
		onEdit={canEdit ? openEdit : undefined}
		onDelete={canDelete ? remove : undefined}
		extraActions={canEdit ? [{ label: '发布/撤回', onClick: togglePublish }] : []}
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

<FormDialog
	bind:open={dialogOpen}
	title={editing?.id ? '编辑公告' : '新增公告'}
	loading={saving}
	onSubmit={save}
	contentClass="sm:max-w-[640px]"
>
	{#if formError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{formError}</div>
	{/if}
	{#if editing}
		<FieldGroup>
			<Field>
				<FieldLabel>标题 *</FieldLabel>
				<Input bind:value={editing.title} />
			</Field>
			<div class="grid grid-cols-2 gap-3">
				<Field>
					<FieldLabel>类型</FieldLabel>
					<Select.Root
						type="single"
						value={String(editing.type ?? 1)}
						onValueChange={(v) => (editing!.type = Number(v) as Notice['type'])}
					>
						<Select.Trigger>{editing.type === 2 ? '公告' : '通知'}</Select.Trigger>
						<Select.Content>
							<Select.Item value="1">通知</Select.Item>
							<Select.Item value="2">公告</Select.Item>
						</Select.Content>
					</Select.Root>
				</Field>
				<Field>
					<FieldLabel>置顶</FieldLabel>
					<Select.Root
						type="single"
						value={String(editing.pinned ?? 0)}
						onValueChange={(v) => (editing!.pinned = Number(v) as Notice['pinned'])}
					>
						<Select.Trigger>{editing.pinned === 1 ? '是' : '否'}</Select.Trigger>
						<Select.Content>
							<Select.Item value="0">否</Select.Item>
							<Select.Item value="1">是</Select.Item>
						</Select.Content>
					</Select.Root>
				</Field>
			</div>
			<Field>
				<FieldLabel>内容</FieldLabel>
				<textarea
					class="border-input bg-background min-h-32 w-full rounded-md border px-3 py-2 text-sm"
					bind:value={editing.content}
				></textarea>
			</Field>
		</FieldGroup>
	{/if}
</FormDialog>
