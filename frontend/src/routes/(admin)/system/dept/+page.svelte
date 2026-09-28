<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/dept';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { commonStatusBadge } from '$lib/components/badges';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { Dept } from '$lib/types/entities';
	import { flattenTree } from '$lib/utils/tree';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let tree = $state<Dept[]>([]);
	let search = $state('');
	let dialogOpen = $state(false);
	let editing = $state<Partial<Dept> | null>(null);
	let formError = $state<string | null>(null);

	const canAdd = $derived(authStore.hasPermission('system:dept:add'));
	const canEdit = $derived(authStore.hasPermission('system:dept:edit'));
	const canDelete = $derived(authStore.hasPermission('system:dept:delete'));
	const flat = $derived.by(() => {
		let rows = flattenTree(tree);
		if (search.trim()) {
			const kw = search.trim().toLowerCase();
			rows = rows.filter((d) => d.name.toLowerCase().includes(kw));
		}
		return rows;
	});

	async function load() {
		loading = true;
		error = null;
		try {
			tree = await api.getDeptTree();
		} catch (e) {
			error = e instanceof Error ? e.message : '加载失败';
		} finally {
			loading = false;
		}
	}

	onMount(load);

	function openAdd() {
		editing = { parentId: 0, status: 0, sort: 0 };
		formError = null;
		dialogOpen = true;
	}

	function openEdit(d: Dept) {
		editing = { ...d };
		formError = null;
		dialogOpen = true;
	}

	async function save() {
		if (!editing?.name?.trim()) {
			formError = '名称不能为空';
			throw new Error(formError);
		}
		saving = true;
		try {
			const payload = {
				name: editing.name,
				parentId: editing.parentId ?? 0,
				sort: editing.sort ?? 0,
				leader: editing.leader,
				phone: editing.phone,
				email: editing.email,
				status: (editing.status ?? 0) as Dept['status'],
				remark: editing.remark
			};
			if (editing.id) await api.updateDept(editing.id, payload);
			else await api.createDept(payload);
			toast.success('部门已保存');
			await load();
		} catch (e) {
			formError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			saving = false;
		}
	}

	async function remove(d: Dept) {
		await api.removeDept(d.id);
		toast.success('已删除');
		await load();
	}

	const columns: CrudColumn<(Dept & { depth: number })>[] = [
		{ header: '名称', accessor: (r) => `${'　'.repeat(r.depth)}${r.name}` },
		{ header: '负责人', accessor: (r) => r.leader ?? '-' },
		{ header: '排序', accessor: (r) => r.sort, class: 'w-16' },
		{ header: '状态', accessor: (r) => r.status, cell: (r) => commonStatusBadge(r.status), class: 'w-20' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div>
		<h2 class="text-lg font-semibold">部门管理</h2>
		<p class="text-muted-foreground text-sm">维护组织树,用户数据范围按部门生效</p>
	</div>
	<CrudTable
		data={flat}
		{columns}
		{loading}
		{error}
		searchValue={search}
		onSearchChange={(v) => (search = v)}
		searchPlaceholder="搜索部门"
		addLabel="新增部门"
		onAdd={canAdd ? openAdd : undefined}
		onEdit={canEdit ? openEdit : undefined}
		onDelete={canDelete ? remove : undefined}
	/>
</div>

<FormDialog bind:open={dialogOpen} title={editing?.id ? '编辑部门' : '新增部门'} loading={saving} onSubmit={save}>
	{#if formError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{formError}</div>
	{/if}
	{#if editing}
		<FieldGroup>
			<Field>
				<FieldLabel>名称 *</FieldLabel>
				<Input bind:value={editing.name} />
			</Field>
			<Field>
				<FieldLabel>上级部门</FieldLabel>
				<Select.Root
					type="single"
					value={String(editing.parentId ?? 0)}
					onValueChange={(v) => (editing!.parentId = Number(v))}
				>
					<Select.Trigger>{flat.find((d) => d.id === editing?.parentId)?.name ?? '根部门'}</Select.Trigger>
					<Select.Content>
						<Select.Item value="0">根部门</Select.Item>
						{#each flat.filter((d) => d.id !== editing?.id) as d (d.id)}
							<Select.Item value={String(d.id)}>{'　'.repeat(d.depth)}{d.name}</Select.Item>
						{/each}
					</Select.Content>
				</Select.Root>
			</Field>
			<Field>
				<FieldLabel>负责人</FieldLabel>
				<Input bind:value={editing.leader} />
			</Field>
			<Field>
				<FieldLabel>排序</FieldLabel>
				<Input type="number" bind:value={editing.sort} />
			</Field>
		</FieldGroup>
	{/if}
</FormDialog>
