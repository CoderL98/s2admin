<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/permission';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { commonStatusBadge, permissionTypeBadge } from '$lib/components/badges';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { Permission } from '$lib/types/entities';
	import { PERMISSION_TYPE_OPTIONS } from '$lib/types/entities';
	import { flattenTree } from '$lib/utils/tree';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let tree = $state<Permission[]>([]);
	let allItems = $state<Permission[]>([]);
	let search = $state('');
	let typeFilter = $state('all');

	let dialogOpen = $state(false);
	let editing = $state<Partial<Permission> | null>(null);
	let formError = $state<string | null>(null);

	const canAdd = $derived(authStore.hasPermission('system:permission:add'));
	const canEdit = $derived(authStore.hasPermission('system:permission:edit'));
	const canDelete = $derived(authStore.hasPermission('system:permission:delete'));

	const parentOptions = $derived(allItems.filter((p) => p.type === 1 || !p.parentId));
	const rows = $derived.by(() => {
		let list = flattenTree(tree);
		if (typeFilter !== 'all') list = list.filter((p) => String(p.type) === typeFilter);
		if (search.trim()) {
			const kw = search.trim().toLowerCase();
			list = list.filter(
				(p) => p.name.toLowerCase().includes(kw) || (p.code ?? '').toLowerCase().includes(kw)
			);
		}
		return list;
	});

	async function load() {
		loading = true;
		error = null;
		try {
			tree = await api.getPermissionTree();
			allItems = await api.getAllPermissions().catch(() => allItems);
		} catch (e) {
			error = e instanceof Error ? e.message : '加载失败';
		} finally {
			loading = false;
		}
	}

	function onSearch(v: string) {
		search = v;
	}

	onMount(async () => {
		await load();
		allItems = await api.getAllPermissions().catch(() => []);
	});

	function openAdd() {
		editing = { type: 2, status: 0, parentId: 0, sort: 0 };
		formError = null;
		dialogOpen = true;
	}

	function openEdit(p: Permission) {
		editing = { ...p };
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
				name: editing.name,
				code: editing.code,
				type: (editing.type ?? 2) as Permission['type'],
				parentId: editing.parentId ?? 0,
				path: editing.path,
				icon: editing.icon,
				sort: editing.sort ?? 0,
				status: (editing.status ?? 0) as Permission['status'],
				remark: editing.remark
			};
			if (editing.id) {
				await api.updatePermission(editing.id, payload);
			} else {
				await api.createPermission(payload);
			}
			toast.success('权限已保存');
			await load();
			allItems = await api.getAllPermissions().catch(() => allItems);
		} catch (e) {
			formError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			saving = false;
		}
	}

	async function remove(p: Permission) {
		await api.removePermission(p.id);
		toast.success('已删除');
		await load();
	}

	function parentLabel(parentId: number): string {
		if (!parentId) return '顶级';
		return allItems.find((x) => x.id === parentId)?.name ?? `#${parentId}`;
	}

	const columns: CrudColumn<Permission>[] = [
		{ header: 'ID', accessor: (r) => r.id, class: 'w-16' },
		{ header: '权限名称', accessor: (r) => `${'　'.repeat((r as Permission & { depth?: number }).depth ?? 0)}${r.name}` },
		{ header: '权限编码', accessor: (r) => r.code, cell: (r) => ({ kind: 'code', text: r.code }) },
		{ header: '类型', accessor: (r) => r.type, cell: (r) => permissionTypeBadge(r.type), class: 'w-24' },
		{ header: '父级', accessor: (r) => parentLabel(r.parentId), class: 'w-28' },
		{ header: '引用角色', accessor: (r) => r.roleCount ?? 0, class: 'w-24' },
		{ header: '排序', accessor: (r) => r.sort, class: 'w-16', align: 'center' },
		{ header: '状态', accessor: (r) => r.status, cell: (r) => commonStatusBadge(r.status), class: 'w-20' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">权限管理</h2>
			<p class="text-muted-foreground text-sm">管理系统权限项,包括菜单/按钮/API</p>
		</div>
		<Select.Root
			type="single"
			bind:value={typeFilter}
			onValueChange={() => {}}
		>
			<Select.Trigger class="w-32">
				{PERMISSION_TYPE_OPTIONS.find((o) => String(o.value) === typeFilter)?.label ?? '全部类型'}
			</Select.Trigger>
			<Select.Content>
				<Select.Item value="all">全部类型</Select.Item>
				{#each PERMISSION_TYPE_OPTIONS as opt (opt.value)}
					<Select.Item value={String(opt.value)}>{opt.label}</Select.Item>
				{/each}
			</Select.Content>
		</Select.Root>
	</div>

	<CrudTable
		data={rows}
		{columns}
		{loading}
		{error}
		searchValue={search}
		onSearchChange={onSearch}
		searchPlaceholder="搜索权限名称或编码"
		addLabel="新增权限"
		onAdd={canAdd ? openAdd : undefined}
		onEdit={canEdit ? openEdit : undefined}
		onDelete={canDelete ? remove : undefined}
	/>
</div>

<FormDialog
	bind:open={dialogOpen}
	title={editing?.id ? '编辑权限' : '新增权限'}
	submitText="保存"
	loading={saving}
	onSubmit={save}
>
	{#if formError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{formError}</div>
	{/if}
	{#if editing}
		<FieldGroup>
			<div class="grid grid-cols-2 gap-3">
				<Field>
					<FieldLabel for="name">权限名称 *</FieldLabel>
					<Input id="name" bind:value={editing.name} />
				</Field>
				<Field>
					<FieldLabel for="code">权限编码 *</FieldLabel>
					<Input id="code" bind:value={editing.code} placeholder="system:user:add" />
				</Field>
				<Field>
					<FieldLabel for="type">类型</FieldLabel>
					<Select.Root
						type="single"
						bind:value={
							() => String(editing!.type ?? 2),
							(v) => (editing!.type = Number(v) as Permission['type'])
						}
					>
						<Select.Trigger id="type">
							{PERMISSION_TYPE_OPTIONS.find((o) => o.value === editing!.type)?.label ?? '按钮'}
						</Select.Trigger>
						<Select.Content>
							{#each PERMISSION_TYPE_OPTIONS as opt (opt.value)}
								<Select.Item value={String(opt.value)}>{opt.label}</Select.Item>
							{/each}
						</Select.Content>
					</Select.Root>
				</Field>
				<Field>
					<FieldLabel for="parentId">父级权限</FieldLabel>
					<Select.Root
						type="single"
						bind:value={() => String(editing!.parentId ?? 0), (v) => (editing!.parentId = Number(v))}
					>
						<Select.Trigger id="parentId">
							{parentOptions.find((p) => p.id === editing!.parentId)?.name ?? '顶级'}
						</Select.Trigger>
						<Select.Content>
							<Select.Item value="0">顶级</Select.Item>
							{#each parentOptions as opt (opt.id)}
								{#if opt.id !== editing.id}
									<Select.Item value={String(opt.id)}>{opt.name}</Select.Item>
								{/if}
							{/each}
						</Select.Content>
					</Select.Root>
				</Field>
				<Field>
					<FieldLabel for="path">API 路径</FieldLabel>
					<Input id="path" bind:value={editing.path} placeholder="/api/system/user" />
				</Field>
				<Field>
					<FieldLabel for="icon">图标</FieldLabel>
					<Input id="icon" bind:value={editing.icon} placeholder="User" />
				</Field>
				<Field>
					<FieldLabel for="sort">排序</FieldLabel>
					<Input id="sort" type="number" bind:value={editing.sort} />
				</Field>
				<Field>
					<FieldLabel for="status">状态</FieldLabel>
					<Select.Root
						type="single"
						bind:value={
							() => String(editing!.status ?? 0),
							(v) => (editing!.status = Number(v) as Permission['status'])
						}
					>
						<Select.Trigger id="status">{['正常', '停用'][editing.status ?? 0]}</Select.Trigger>
						<Select.Content>
							<Select.Item value="0">正常</Select.Item>
							<Select.Item value="1">停用</Select.Item>
						</Select.Content>
					</Select.Root>
				</Field>
			</div>
		</FieldGroup>
	{/if}
</FormDialog>
