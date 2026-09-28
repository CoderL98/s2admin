<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/role';
	import * as permApi from '$lib/api/permission';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import PermissionPicker from '$lib/components/permission-picker.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { commonStatusBadge, dataScopeBadge, dataScopeLabel } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { Permission, Role } from '$lib/types/entities';
	import { DATA_SCOPE_OPTIONS } from '$lib/types/entities';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<Role[]>([]);
	let search = $state('');
	let statusFilter = $state('all');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);

	let dialogOpen = $state(false);
	let editing = $state<Partial<Role> | null>(null);
	let formError = $state<string | null>(null);

	let assignOpen = $state(false);
	let assignRole = $state<Role | null>(null);
	let assignIds = $state<number[]>([]);
	let permissions = $state<Permission[]>([]);
	let assignSaving = $state(false);

	const canAdd = $derived(authStore.hasPermission('system:role:add'));
	const canEdit = $derived(authStore.hasPermission('system:role:edit'));
	const canDelete = $derived(authStore.hasPermission('system:role:delete'));
	const canAssign = $derived(authStore.hasPermission('system:role:assign'));

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await api.getRoleList({
				pageNum,
				pageSize,
				keyword: search || undefined,
				status: statusFilter === 'all' ? undefined : Number(statusFilter)
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

	function openAdd() {
		editing = { status: 0, dataScope: 4, sort: 0 };
		formError = null;
		dialogOpen = true;
	}

	function openEdit(r: Role) {
		editing = { ...r };
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
				sort: editing.sort ?? 0,
				dataScope: (editing.dataScope ?? 4) as Role['dataScope'],
				status: (editing.status ?? 0) as Role['status'],
				remark: editing.remark
			};
			if (editing.id) {
				await api.updateRole(editing.id, payload);
			} else {
				await api.createRole(payload);
			}
			toast.success('角色已保存');
			await load();
		} catch (e) {
			formError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			saving = false;
		}
	}

	async function remove(r: Role) {
		await api.removeRole(r.id);
		toast.success('已删除');
		await load();
	}

	async function openAssign(r: Role) {
		assignRole = r;
		assignIds = [];
		assignOpen = true;
		permissions = await permApi.getAllPermissions().catch(() => []);
		assignIds = await api.getRolePermissionIds(r.id).catch(() => []);
	}

	async function saveAssign() {
		if (!assignRole) return;
		assignSaving = true;
		try {
			await api.assignRolePermissions(assignRole.id, assignIds);
			toast.success('权限已更新');
		} finally {
			assignSaving = false;
		}
	}

	const columns: CrudColumn<Role>[] = [
		{ header: 'ID', accessor: (r) => r.id, class: 'w-16' },
		{ header: '角色名称', accessor: (r) => r.name },
		{ header: '角色编码', accessor: (r) => r.code, cell: (r) => ({ kind: 'code', text: r.code }) },
		{ header: '排序', accessor: (r) => r.sort, class: 'w-16', align: 'center' },
		{ header: '数据范围', accessor: (r) => r.dataScope, cell: (r) => dataScopeBadge(r.dataScope), class: 'w-32' },
		{ header: '用户数', accessor: (r) => r.userCount ?? 0, class: 'w-20' },
		{ header: '状态', accessor: (r) => r.status, cell: (r) => commonStatusBadge(r.status), class: 'w-20' },
		{ header: '备注', accessor: (r) => r.remark ?? '-' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">角色管理</h2>
			<p class="text-muted-foreground text-sm">角色决定菜单、按钮和数据范围。授权时不能超出你自己的权限。</p>
		</div>
		<Select.Root
			type="single"
			value={statusFilter}
			onValueChange={(v) => {
				statusFilter = v ?? 'all';
				pageNum = 1;
				load();
			}}
		>
			<Select.Trigger class="w-32">{statusFilter === '1' ? '停用' : statusFilter === '0' ? '正常' : '全部状态'}</Select.Trigger>
			<Select.Content>
				<Select.Item value="all">全部状态</Select.Item>
				<Select.Item value="0">正常</Select.Item>
				<Select.Item value="1">停用</Select.Item>
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
		searchPlaceholder="搜索角色名称或编码"
		addLabel="新增角色"
		onAdd={canAdd ? openAdd : undefined}
		onEdit={canEdit ? openEdit : undefined}
		onDelete={canDelete ? remove : undefined}
		extraActions={canAssign ? [{ label: '分配权限', onClick: openAssign }] : []}
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
	title={editing?.id ? '编辑角色' : '新增角色'}
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
					<FieldLabel for="name">角色名称 *</FieldLabel>
					<Input id="name" bind:value={editing.name} />
				</Field>
				<Field>
					<FieldLabel for="code">角色编码 *</FieldLabel>
					<Input
						id="code"
						bind:value={editing.code}
						placeholder="大写字母+下划线"
						disabled={editing.code === 'SUPER_ADMIN'}
					/>
				</Field>
				<Field>
					<FieldLabel for="sort">排序</FieldLabel>
					<Input id="sort" type="number" bind:value={editing.sort} />
				</Field>
				<Field>
					<FieldLabel for="dataScope">数据范围</FieldLabel>
					<Select.Root
						type="single"
						bind:value={
							() => String(editing!.dataScope ?? 4),
							(v) => (editing!.dataScope = Number(v) as Role['dataScope'])
						}
					>
						<Select.Trigger id="dataScope">
							{dataScopeLabel((editing.dataScope ?? 4) as Role['dataScope'])}
						</Select.Trigger>
						<Select.Content>
							{#each DATA_SCOPE_OPTIONS as opt (opt.value)}
								<Select.Item value={String(opt.value)}>{opt.label}</Select.Item>
							{/each}
						</Select.Content>
					</Select.Root>
				</Field>
				<Field>
					<FieldLabel for="status">状态</FieldLabel>
					<Select.Root
						type="single"
						bind:value={() => String(editing!.status ?? 0), (v) => (editing!.status = Number(v) as Role['status'])}
					>
						<Select.Trigger id="status">{['正常', '停用'][editing.status ?? 0]}</Select.Trigger>
						<Select.Content>
							<Select.Item value="0">正常</Select.Item>
							<Select.Item value="1">停用</Select.Item>
						</Select.Content>
					</Select.Root>
				</Field>
			</div>
			<Field>
				<FieldLabel for="remark">备注</FieldLabel>
				<Input id="remark" bind:value={editing.remark} />
			</Field>
		</FieldGroup>
	{/if}
</FormDialog>

<FormDialog
	bind:open={assignOpen}
	title="分配权限"
	description={assignRole ? `为「${assignRole.name}」勾选可访问的权限` : ''}
	submitText="保存授权"
	loading={assignSaving}
	onSubmit={saveAssign}
	contentClass="sm:max-w-[680px]"
>
	<PermissionPicker items={permissions} bind:selectedIds={assignIds} />
</FormDialog>
