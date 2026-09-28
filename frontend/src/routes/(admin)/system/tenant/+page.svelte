<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/tenant';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { commonStatusBadge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { Tenant } from '$lib/types/entities';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<Tenant[]>([]);
	let search = $state('');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);
	let dialogOpen = $state(false);
	let editing = $state<Partial<Tenant> | null>(null);
	let formError = $state<string | null>(null);

	const canAdd = $derived(authStore.hasPermission('system:tenant:add'));
	const canEdit = $derived(authStore.hasPermission('system:tenant:edit'));
	const canDelete = $derived(authStore.hasPermission('system:tenant:delete'));

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await api.getTenantList({ pageNum, pageSize, keyword: search || undefined });
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

	function openAdd() {
		editing = { status: 0, code: '', name: '', contact: '' };
		formError = null;
		dialogOpen = true;
	}

	function openEdit(row: Tenant) {
		editing = { ...row };
		formError = null;
		dialogOpen = true;
	}

	async function save() {
		if (!editing) return;
		formError = null;
		if (!editing.name?.trim() || !editing.code?.trim()) {
			formError = '名称和编码不能为空';
			throw new Error(formError);
		}
		saving = true;
		try {
			const payload = {
				name: editing.name.trim(),
				code: editing.code.trim(),
				status: editing.status ?? 0,
				contact: editing.contact,
				remark: editing.remark
			};
			if (editing.id) await api.updateTenant(editing.id, payload);
			else await api.createTenant(payload);
			toast.success('租户已保存');
			await load();
		} catch (e) {
			formError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			saving = false;
		}
	}

	async function remove(row: Tenant) {
		await api.removeTenant(row.id);
		toast.success('已删除');
		await load();
	}

	const columns: CrudColumn<Tenant>[] = [
		{ header: '名称', accessor: (r) => r.name },
		{ header: '编码', accessor: (r) => r.code, cell: (r) => ({ kind: 'code', text: r.code }) },
		{ header: '用户数', accessor: (r) => r.userCount ?? 0, class: 'w-20' },
		{ header: '联系人', accessor: (r) => r.contact ?? '-' },
		{ header: '状态', accessor: (r) => r.status, cell: (r) => commonStatusBadge(r.status), class: 'w-20' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div>
		<h2 class="text-lg font-semibold">租户管理</h2>
		<p class="text-muted-foreground text-sm">
			平台超级管理员看全部数据。顶栏可以选择一个租户,请求会带上 X-Tenant-Id。普通用户只能看到自己租户的用户、审批和文件。
		</p>
	</div>
	<CrudTable
		data={rows}
		{columns}
		{loading}
		{error}
		searchValue={search}
		onSearchChange={onSearch}
		searchPlaceholder="搜索租户名称或编码"
		addLabel="新增租户"
		onAdd={canAdd ? openAdd : undefined}
		onEdit={canEdit ? openEdit : undefined}
		onDelete={canDelete
			? async (row) => {
					if (row.code === 'DEFAULT') {
						toast.error('不能删除默认租户');
						return;
					}
					await remove(row);
				}
			: undefined}
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

<FormDialog bind:open={dialogOpen} title={editing?.id ? '编辑租户' : '新增租户'} submitText="保存" loading={saving} onSubmit={save}>
	{#if formError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{formError}</div>
	{/if}
	{#if editing}
		<FieldGroup>
			<div class="grid grid-cols-2 gap-3">
				<Field>
					<FieldLabel for="tenant-name">名称 *</FieldLabel>
					<Input id="tenant-name" bind:value={editing.name} />
				</Field>
				<Field>
					<FieldLabel for="tenant-code">编码 *</FieldLabel>
					<Input id="tenant-code" bind:value={editing.code} placeholder="ACME" disabled={editing.code === 'DEFAULT'} />
				</Field>
			</div>
			<Field>
				<FieldLabel for="tenant-contact">联系人</FieldLabel>
				<Input id="tenant-contact" bind:value={editing.contact} />
			</Field>
			<Field>
				<FieldLabel>状态</FieldLabel>
				<Select.Root
					type="single"
					value={String(editing.status ?? 0)}
					onValueChange={(v) => (editing && (editing.status = Number(v) as 0 | 1))}
				>
					<Select.Trigger>{editing.status === 1 ? '停用' : '正常'}</Select.Trigger>
					<Select.Content>
						<Select.Item value="0">正常</Select.Item>
						<Select.Item value="1" disabled={editing.code === 'DEFAULT'}>停用</Select.Item>
					</Select.Content>
				</Select.Root>
			</Field>
			<Field>
				<FieldLabel for="tenant-remark">说明</FieldLabel>
				<Input id="tenant-remark" bind:value={editing.remark} />
			</Field>
		</FieldGroup>
	{/if}
</FormDialog>
