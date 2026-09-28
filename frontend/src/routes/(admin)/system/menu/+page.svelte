<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/menu';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { commonStatusBadge, menuTypeBadge } from '$lib/components/badges';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { Menu } from '$lib/types/entities';
	import { MENU_TYPE_OPTIONS } from '$lib/types/entities';
	import { flattenTree } from '$lib/utils/tree';
	import { MENU_ICON_OPTIONS } from '$lib/icons';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let tree = $state<Menu[]>([]);
	let search = $state('');
	let typeFilter = $state('all');

	let dialogOpen = $state(false);
	let editing = $state<Partial<Menu> | null>(null);
	let formError = $state<string | null>(null);

	const canAdd = $derived(authStore.hasPermission('system:menu:add'));
	const canEdit = $derived(authStore.hasPermission('system:menu:edit'));
	const canDelete = $derived(authStore.hasPermission('system:menu:delete'));

	const flat = $derived.by(() => {
		let rows = flattenTree(tree);
		if (typeFilter !== 'all') rows = rows.filter((m) => String(m.type) === typeFilter);
		if (search.trim()) {
			const kw = search.trim().toLowerCase();
			rows = rows.filter(
				(m) => m.name.toLowerCase().includes(kw) || (m.path ?? '').toLowerCase().includes(kw)
			);
		}
		return rows;
	});

	const parentOptions = $derived(flattenTree(tree).filter((m) => m.type !== 3));

	async function load() {
		loading = true;
		error = null;
		try {
			tree = await api.getMenuTree();
		} catch (e) {
			error = e instanceof Error ? e.message : '加载失败';
		} finally {
			loading = false;
		}
	}

	onMount(load);

	function openAdd() {
		editing = { type: 2, status: 0, hidden: 0, sort: 0, parentId: 0 };
		formError = null;
		dialogOpen = true;
	}

	function openEdit(m: Menu) {
		editing = { ...m };
		formError = null;
		dialogOpen = true;
	}

	async function save() {
		if (!editing) return;
		formError = null;
		if (!editing.name?.trim()) {
			formError = '名称不能为空';
			throw new Error(formError);
		}
		if (editing.type !== 3 && !editing.path?.trim()) {
			formError = '目录和菜单必须填写路由路径';
			throw new Error(formError);
		}
		saving = true;
		try {
			const payload = {
				name: editing.name,
				parentId: editing.parentId ?? 0,
				path: editing.path ?? '',
				component: editing.component,
				redirect: editing.redirect,
				permission: editing.permission,
				icon: editing.icon,
				sort: editing.sort ?? 0,
				type: (editing.type ?? 2) as Menu['type'],
				hidden: (editing.hidden ?? 0) as Menu['hidden'],
				status: (editing.status ?? 0) as Menu['status'],
				remark: editing.remark
			};
			if (editing.id) {
				await api.updateMenu(editing.id, payload);
			} else {
				await api.createMenu(payload);
			}
			toast.success('菜单已保存');
			await load();
		} catch (e) {
			formError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			saving = false;
		}
	}

	async function remove(m: Menu) {
		await api.removeMenu(m.id);
		toast.success('已删除');
		await load();
	}

	async function move(m: Menu, direction: 'up' | 'down') {
		await api.moveMenu(m.id, direction);
		await load();
	}

	function countDescendants(node: Menu): number {
		return (node.children ?? []).reduce((sum, child) => sum + 1 + countDescendants(child), 0);
	}

	const columns: CrudColumn<Menu & { depth: number }>[] = [
		{
			header: '菜单名称',
			accessor: (r) => r.name,
			cell: (r) => ({ kind: 'text', text: `${'　'.repeat(r.depth)}${r.name}` })
		},
		{ header: '类型', accessor: (r) => r.type, cell: (r) => menuTypeBadge(r.type), class: 'w-20' },
		{ header: '路由路径', accessor: (r) => r.path, cell: (r) => ({ kind: 'code', text: r.path || '-' }) },
		{ header: '组件', accessor: (r) => r.component ?? '-' },
		{ header: '权限标识', accessor: (r) => r.permission ?? '-' },
		{ header: '排序', accessor: (r) => r.sort, class: 'w-16', align: 'center' },
		{ header: '状态', accessor: (r) => r.status, cell: (r) => commonStatusBadge(r.status), class: 'w-20' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">菜单管理</h2>
			<p class="text-muted-foreground text-sm">以树形结构维护目录、菜单与按钮</p>
		</div>
		<Select.Root type="single" bind:value={typeFilter}>
			<Select.Trigger class="w-32">
				{MENU_TYPE_OPTIONS.find((o) => String(o.value) === typeFilter)?.label ?? '全部类型'}
			</Select.Trigger>
			<Select.Content>
				<Select.Item value="all">全部类型</Select.Item>
				{#each MENU_TYPE_OPTIONS as opt (opt.value)}
					<Select.Item value={String(opt.value)}>{opt.label}</Select.Item>
				{/each}
			</Select.Content>
		</Select.Root>
	</div>

	<CrudTable
		data={flat}
		{columns}
		{loading}
		{error}
		searchValue={search}
		onSearchChange={(v) => (search = v)}
		searchPlaceholder="搜索菜单名称或路径"
		addLabel="新增菜单"
		onAdd={canAdd ? openAdd : undefined}
		onEdit={canEdit ? openEdit : undefined}
		onDelete={canDelete ? remove : undefined}
		extraActions={
			canEdit
				? [
						{ label: '上移', onClick: (m) => move(m, 'up') },
						{ label: '下移', onClick: (m) => move(m, 'down') }
					]
				: []
		}
		deleteConfirmText={(m) => {
			const n = countDescendants(m);
			return n > 0
				? `将同时删除 ${n} 个子菜单/按钮,此操作不可撤销。`
				: '此操作不可撤销,删除后将无法恢复。';
		}}
	/>
</div>

<FormDialog
	bind:open={dialogOpen}
	title={editing?.id ? '编辑菜单' : '新增菜单'}
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
					<FieldLabel for="name">菜单名称 *</FieldLabel>
					<Input id="name" bind:value={editing.name} />
				</Field>
				<Field>
					<FieldLabel for="type">类型</FieldLabel>
					<Select.Root
						type="single"
						bind:value={() => String(editing!.type ?? 2), (v) => (editing!.type = Number(v) as Menu['type'])}
					>
						<Select.Trigger id="type">
							{MENU_TYPE_OPTIONS.find((o) => o.value === editing!.type)?.label ?? '菜单'}
						</Select.Trigger>
						<Select.Content>
							{#each MENU_TYPE_OPTIONS as opt (opt.value)}
								<Select.Item value={String(opt.value)}>{opt.label}</Select.Item>
							{/each}
						</Select.Content>
					</Select.Root>
				</Field>
				<Field>
					<FieldLabel for="parentId">父级菜单</FieldLabel>
					<Select.Root
						type="single"
						bind:value={() => String(editing!.parentId ?? 0), (v) => (editing!.parentId = Number(v))}
					>
						<Select.Trigger id="parentId">
							{parentOptions.find((m) => m.id === editing!.parentId)?.name ?? '顶级'}
						</Select.Trigger>
						<Select.Content>
							<Select.Item value="0">顶级</Select.Item>
							{#each parentOptions as opt (opt.id)}
								{#if opt.id !== editing.id}
									<Select.Item value={String(opt.id)}>{'　'.repeat(opt.depth)}{opt.name}</Select.Item>
								{/if}
							{/each}
						</Select.Content>
					</Select.Root>
				</Field>
				<Field>
					<FieldLabel for="path">路由路径</FieldLabel>
					<Input
						id="path"
						bind:value={editing.path}
						placeholder={editing.type === 3 ? '按钮可留空' : '/system/user 或 https://example.com'}
					/>
					<p class="text-muted-foreground text-xs">以 http:// 或 https:// 开头时作为外链,侧栏会在新标签页打开。</p>
				</Field>
				<Field>
					<FieldLabel for="component">组件路径</FieldLabel>
					<Input id="component" bind:value={editing.component} placeholder="/system/user/index" />
				</Field>
				<Field>
					<FieldLabel for="icon">图标</FieldLabel>
					<Input id="icon" bind:value={editing.icon} placeholder="User / Setting" list="menu-icon-options" />
					<div class="mt-2 flex flex-wrap gap-1">
						{#each MENU_ICON_OPTIONS as name (name)}
							<button
								type="button"
								class="rounded-md border px-2 py-1 text-xs {editing.icon === name
									? 'border-primary bg-primary/10'
									: 'border-input hover:bg-muted'}"
								onclick={() => (editing!.icon = name)}
							>
								{name}
							</button>
						{/each}
					</div>
					<datalist id="menu-icon-options">
						{#each MENU_ICON_OPTIONS as name (name)}
							<option value={name}>{name}</option>
						{/each}
					</datalist>
				</Field>
				<Field>
					<FieldLabel for="permission">权限标识</FieldLabel>
					<Input id="permission" bind:value={editing.permission} placeholder="system:user:view" />
				</Field>
				<Field>
					<FieldLabel for="sort">排序</FieldLabel>
					<Input id="sort" type="number" bind:value={editing.sort} />
				</Field>
				<Field>
					<FieldLabel for="hidden">显示</FieldLabel>
					<Select.Root
						type="single"
						bind:value={() => String(editing!.hidden ?? 0), (v) => (editing!.hidden = Number(v) as Menu['hidden'])}
					>
						<Select.Trigger id="hidden">{editing.hidden === 1 ? '隐藏' : '显示'}</Select.Trigger>
						<Select.Content>
							<Select.Item value="0">显示</Select.Item>
							<Select.Item value="1">隐藏</Select.Item>
						</Select.Content>
					</Select.Root>
				</Field>
				<Field>
					<FieldLabel for="status">状态</FieldLabel>
					<Select.Root
						type="single"
						bind:value={() => String(editing!.status ?? 0), (v) => (editing!.status = Number(v) as Menu['status'])}
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
