<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/user';
	import * as roleApi from '$lib/api/role';
	import * as deptApi from '$lib/api/dept';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import DictSelect from '$lib/components/dict-select.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { Checkbox } from '$lib/components/ui/checkbox/index.js';
	import { userStatusBadge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import { dayRange } from '$lib/utils/datetime';
	import { isStrongPassword, PASSWORD_HINT } from '$lib/utils/password';
	import type { User, Role, Dept } from '$lib/types/entities';
	import { flattenTree } from '$lib/utils/tree';
	import { toast } from 'svelte-sonner';
	import RegionSelect from '$lib/components/region-select.svelte';

	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<User[]>([]);
	let roles = $state<Role[]>([]);
	let depts = $state<(Dept & { depth: number })[]>([]);
	let search = $state('');
	let statusFilter = $state('');
	let roleFilter = $state('__all__');
	let deptFilter = $state('__all__');
	let beginDate = $state('');
	let endDate = $state('');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);
	let selectedIds = $state<(number | string)[]>([]);

	let dialogOpen = $state(false);
	let editing = $state<Partial<User> | null>(null);
	let formError = $state<string | null>(null);

	let resetOpen = $state(false);
	let resetTarget = $state<User | null>(null);
	let resetPassword = $state('Admin@123');

	const EXPORT_FIELDS = [
		{ key: 'username', label: '用户名' },
		{ key: 'nickname', label: '昵称' },
		{ key: 'email', label: '邮箱' },
		{ key: 'phone', label: '手机号' },
		{ key: 'deptName', label: '部门' },
		{ key: 'status', label: '状态' }
	] as const;
	let exportOpen = $state(false);
	let exportKind = $state<'csv' | 'xlsx'>('csv');
	let exportFields = $state<string[]>(EXPORT_FIELDS.map((f) => f.key));

	const canAdd = $derived(authStore.hasPermission('system:user:add'));
	const canEdit = $derived(authStore.hasPermission('system:user:edit'));
	const canDelete = $derived(authStore.hasPermission('system:user:delete'));
	const isSuper = $derived(authStore.currentUser?.roles.includes('SUPER_ADMIN') ?? false);
	const assignableRoles = $derived(
		roles.filter((r) => r.status === 0 && (isSuper || r.code !== 'SUPER_ADMIN'))
	);

	async function load() {
		loading = true;
		error = null;
		try {
			const range = dayRange(beginDate, endDate);
			const res = await api.getUserList({
				pageNum,
				pageSize,
				keyword: search || undefined,
				status: statusFilter ? Number(statusFilter) : undefined,
				roleId: roleFilter && roleFilter !== '__all__' ? Number(roleFilter) : undefined,
				deptId: deptFilter && deptFilter !== '__all__' ? Number(deptFilter) : undefined,
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

	let searchDebounce: ReturnType<typeof setTimeout>;
	function onSearch(v: string) {
		search = v;
		clearTimeout(searchDebounce);
		searchDebounce = setTimeout(() => {
			pageNum = 1;
			load();
		}, 200);
	}

	onMount(async () => {
		await load();
		roles = await roleApi.getAllRoles().catch(() => []);
		try {
			depts = flattenTree(await deptApi.getDeptTree());
		} catch {
			depts = (await deptApi.getDeptOptions().catch(() => [])).map((d) => ({ ...d, depth: 0 }));
		}
	});

	function openAdd() {
		editing = { status: 0, roleIds: [], province: '', city: '', district: '' };
		formError = null;
		dialogOpen = true;
	}

	function openEdit(row: User) {
		editing = {
			...row,
			roleIds: row.roleIds ?? [],
			province: row.province ?? '',
			city: row.city ?? '',
			district: row.district ?? ''
		};
		formError = null;
		dialogOpen = true;
	}

	function toggleRole(id: number, checked: boolean) {
		if (!editing) return;
		const ids = editing.roleIds ?? [];
		editing.roleIds = checked ? [...ids, id] : ids.filter((x) => x !== id);
	}

	async function save() {
		if (!editing) return;
		formError = null;
		if (!editing.username?.trim() || !editing.nickname?.trim()) {
			formError = '用户名和昵称不能为空';
			throw new Error(formError);
		}
		saving = true;
		try {
			const payload = {
				username: editing.username,
				nickname: editing.nickname,
				email: editing.email?.trim() || undefined,
				phone: editing.phone?.trim() || undefined,
				avatar: editing.avatar || undefined,
				status: editing.status ?? 0,
				deptId: editing.deptId,
				roleIds: editing.roleIds ?? [],
				province: editing.province,
				city: editing.city,
				district: editing.district,
				remark: editing.remark
			};
			if (editing.id) {
				await api.updateUser(editing.id, payload);
				toast.success('用户已更新');
			} else {
				await api.createUser(payload);
				toast.success('用户已创建,请使用「重置密码」发放初始口令');
			}
			await load();
		} catch (e) {
			formError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			saving = false;
		}
	}

	async function remove(row: User) {
		await api.removeUser(row.id);
		toast.success('已删除');
		selectedIds = selectedIds.filter((id) => id !== row.id);
		await load();
	}

	async function batchRemove() {
		const ids = selectedIds.map(Number).filter((n) => !Number.isNaN(n));
		if (!ids.length) return;
		if (!confirm(`确认删除选中的 ${ids.length} 个用户?`)) return;
		await api.batchRemoveUsers(ids);
		toast.success('已批量删除');
		selectedIds = [];
		await load();
	}

	async function batchStatus(status: number) {
		const ids = selectedIds.map(Number).filter((n) => !Number.isNaN(n));
		if (!ids.length) return;
		await api.batchUpdateUserStatus(ids, status);
		toast.success(status === 0 ? '已批量启用' : '已批量禁用');
		selectedIds = [];
		await load();
	}

	async function toggleStatus(row: User) {
		if (row.status !== 0 && row.status !== 1) return;
		const next = row.status === 0 ? 1 : 0;
		await api.updateUserStatus(row.id, next);
		toast.success(next === 0 ? '已启用' : '已禁用');
		await load();
	}

	function openReset(row: User) {
		resetTarget = row;
		resetPassword = 'Admin@123';
		resetOpen = true;
	}

	function currentExportQuery() {
		const range = dayRange(beginDate, endDate);
		return {
			keyword: search || undefined,
			status: statusFilter ? Number(statusFilter) : undefined,
			roleId: roleFilter && roleFilter !== '__all__' ? Number(roleFilter) : undefined,
			deptId: deptFilter && deptFilter !== '__all__' ? Number(deptFilter) : undefined,
			beginTime: range.beginTime,
			endTime: range.endTime,
			fields: exportFields.join(',')
		};
	}

	function openExport(kind: 'csv' | 'xlsx') {
		exportKind = kind;
		if (!exportFields.length) exportFields = EXPORT_FIELDS.map((f) => f.key);
		exportOpen = true;
	}

	function toggleExportField(key: string, checked: boolean) {
		exportFields = checked ? [...exportFields, key] : exportFields.filter((k) => k !== key);
	}

	async function doExport() {
		if (!exportFields.length) {
			throw new Error('请至少选择一个导出字段');
		}
		const q = currentExportQuery();
		if (exportKind === 'xlsx') {
			await api.exportUsersXlsx(q);
			toast.success('已开始下载 Excel');
		} else {
			await api.exportUsers(q);
			toast.success('已开始下载');
		}
	}

	async function doTemplate() {
		try {
			await api.downloadUserTemplate();
		} catch (e) {
			toast.error(e instanceof Error ? e.message : '下载模板失败');
		}
	}

	async function onImport(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		if (!file) return;
		try {
			const result = await api.importUsers(file);
			toast.success(`导入完成: 新增 ${result.created}, 跳过 ${result.skipped}`);
			if (result.errors.length) {
				toast.message(result.errors.slice(0, 3).join('；'));
			}
			await load();
		} catch (err) {
			toast.error(err instanceof Error ? err.message : '导入失败');
		} finally {
			input.value = '';
		}
	}

	async function onImportXlsx(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		if (!file) return;
		try {
			const result = await api.importUsersXlsx(file);
			toast.success(`导入完成: 新增 ${result.created}, 跳过 ${result.skipped}`);
			if (result.errors.length) {
				toast.message(result.errors.slice(0, 3).join('；'));
			}
			await load();
		} catch (err) {
			toast.error(err instanceof Error ? err.message : '导入失败');
		} finally {
			input.value = '';
		}
	}

	async function saveReset() {
		if (!resetTarget) return;
		if (!isStrongPassword(resetPassword)) {
			throw new Error(PASSWORD_HINT);
		}
		await api.resetUserPassword(resetTarget.id, resetPassword);
		toast.success('密码已重置');
	}

	const columns: CrudColumn<User>[] = [
		{ header: 'ID', accessor: (r) => r.id, class: 'w-16' },
		{ header: '用户名', accessor: (r) => r.username },
		{ header: '昵称', accessor: (r) => r.nickname },
		{ header: '邮箱', accessor: (r) => r.email },
		{ header: '手机号', accessor: (r) => r.phone, class: 'w-32' },
		{ header: '部门', accessor: (r) => r.deptName ?? '-', class: 'w-24' },
		{
			header: '角色',
			accessor: (r) => (r.roleNames?.length ? r.roleNames.join('、') : '-'),
			class: 'w-40'
		},
		{
			header: '状态',
			accessor: (r) => r.status,
			cell: (r) => {
				const st = userStatusBadge(r.status);
				if (r.pwdReset === 1 && st.kind === 'badge') {
					return { kind: 'badge' as const, text: `${st.text}·待改密`, tone: st.tone };
				}
				return st;
			},
			class: 'w-20'
		},
		{ header: '创建时间', accessor: (r) => r.createTime ?? '', class: 'w-40' }
	];

	let detail = $state<User | null>(null);
	let detailOpen = $state(false);
	function openDetail(row: User) {
		detail = row;
		detailOpen = true;
	}

	const statusLabel: Record<number, string> = { 0: '正常', 1: '禁用', 2: '锁定', 3: '过期' };

	const extraActions = $derived([
		...(canEdit
			? [
					{ label: '重置密码', onClick: openReset },
					{
						label: '切换启用',
						onClick: toggleStatus,
						show: (row: User) => row.status === 0 || row.status === 1
					},
					{
						label: '解锁',
						onClick: async (row: User) => {
							await api.updateUserStatus(row.id, 0);
							toast.success('已解锁');
							await load();
						},
						show: (row: User) => row.status === 2 || row.status === 3
					},
					{ label: '查看', onClick: openDetail }
				]
			: [])
	]);
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">用户管理</h2>
			<p class="text-muted-foreground text-sm">管理系统用户账号、角色与状态</p>
		</div>
		<div class="flex flex-wrap items-center gap-2">
			<DictSelect
				typeCode="user_status"
				bind:value={statusFilter}
				allowEmpty
				emptyLabel="全部状态"
				class="w-32"
				onValueChange={() => {
					pageNum = 1;
					load();
				}}
			/>
			<Select.Root
				type="single"
				bind:value={deptFilter}
				onValueChange={() => {
					pageNum = 1;
					load();
				}}
			>
				<Select.Trigger class="w-36">
					{depts.find((d) => String(d.id) === deptFilter)?.name ?? '全部部门'}
				</Select.Trigger>
				<Select.Content>
					<Select.Item value="__all__">全部部门</Select.Item>
					{#each depts as d (d.id)}
						<Select.Item value={String(d.id)}>{'　'.repeat(d.depth)}{d.name}</Select.Item>
					{/each}
				</Select.Content>
			</Select.Root>
			<Select.Root
				type="single"
				bind:value={roleFilter}
				onValueChange={() => {
					pageNum = 1;
					load();
				}}
			>
				<Select.Trigger class="w-36">
					{roles.find((r) => String(r.id) === roleFilter)?.name ?? '全部角色'}
				</Select.Trigger>
				<Select.Content>
					<Select.Item value="__all__">全部角色</Select.Item>
					{#each roles as role (role.id)}
						<Select.Item value={String(role.id)}>{role.name}</Select.Item>
					{/each}
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
			{#if canAdd || canEdit}
				<Button variant="outline" size="sm" onclick={() => openExport('csv')}>导出 CSV</Button>
				<Button variant="outline" size="sm" onclick={() => openExport('xlsx')}>导出 Excel</Button>
			{/if}
			{#if canAdd}
				<Button variant="outline" size="sm" onclick={doTemplate}>导入模板</Button>
				<Button variant="outline" size="sm" class="relative">
					导入 CSV
					<input
						type="file"
						accept=".csv,text/csv"
						class="absolute inset-0 cursor-pointer opacity-0"
						onchange={onImport}
					/>
				</Button>
				<Button variant="outline" size="sm" class="relative">
					导入 Excel
					<input
						type="file"
						accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
						class="absolute inset-0 cursor-pointer opacity-0"
						onchange={onImportXlsx}
					/>
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
		searchPlaceholder="搜索用户名/昵称/邮箱/手机号"
		addLabel="新增用户"
		onAdd={canAdd ? openAdd : undefined}
		onEdit={canEdit ? openEdit : undefined}
		onDelete={canDelete ? remove : undefined}
		{extraActions}
		{pageNum}
		{pageSize}
		{total}
		selectable={canDelete || canEdit}
		bind:selectedIds
		batchActions={[
			...(canDelete ? [{ label: '批量删除', variant: 'destructive' as const, onClick: batchRemove }] : []),
			...(canEdit
				? [
						{ label: '批量启用', variant: 'outline' as const, onClick: () => batchStatus(0) },
						{ label: '批量禁用', variant: 'outline' as const, onClick: () => batchStatus(1) }
					]
				: [])
		]}
		onPageChange={(p, s) => {
			pageNum = p;
			pageSize = s;
			load();
		}}
	/>
</div>

<FormDialog
	bind:open={dialogOpen}
	title={editing?.id ? '编辑用户' : '新增用户'}
	description={editing?.id ? '留空的邮箱、手机号会清空;未改头像会保留。' : '未填写密码时系统生成随机强口令,请用「重置密码」发给用户。用户首次登录必须修改密码。'}
	submitText={editing?.id ? '保存' : '创建'}
	loading={saving}
	onSubmit={save}
	contentClass="sm:max-w-[640px]"
>
	{#if formError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{formError}</div>
	{/if}
	{#if editing}
		<FieldGroup>
			<div class="grid grid-cols-2 gap-3">
				<Field>
					<FieldLabel for="username">用户名 *</FieldLabel>
					<Input id="username" bind:value={editing.username} disabled={!!editing.id} />
				</Field>
				<Field>
					<FieldLabel for="nickname">昵称 *</FieldLabel>
					<Input id="nickname" bind:value={editing.nickname} />
				</Field>
				<Field>
					<FieldLabel for="email">邮箱</FieldLabel>
					<Input id="email" type="email" bind:value={editing.email} />
				</Field>
				<Field>
					<FieldLabel for="phone">手机号</FieldLabel>
					<Input id="phone" bind:value={editing.phone} />
				</Field>
				<Field>
					<FieldLabel for="deptId">部门</FieldLabel>
					<Select.Root
						type="single"
						value={editing.deptId ? String(editing.deptId) : '0'}
						onValueChange={(v) => {
							editing!.deptId = v && v !== '0' ? Number(v) : undefined;
						}}
					>
						<Select.Trigger id="deptId">
							{depts.find((d) => d.id === editing?.deptId)?.name ?? '未分配'}
						</Select.Trigger>
						<Select.Content>
							<Select.Item value="0">未分配</Select.Item>
							{#each depts as d (d.id)}
								<Select.Item value={String(d.id)}>{'　'.repeat(d.depth)}{d.name}</Select.Item>
							{/each}
						</Select.Content>
					</Select.Root>
				</Field>
				<Field>
					<FieldLabel for="status">状态</FieldLabel>
					<DictSelect
						id="status"
						typeCode="user_status"
						value={String(editing.status ?? 0)}
						onValueChange={(v) => (editing!.status = Number(v) as User['status'])}
					/>
				</Field>
			</div>
			<Field>
				<FieldLabel>所在地区</FieldLabel>
				<RegionSelect bind:province={editing.province} bind:city={editing.city} bind:district={editing.district} />
			</Field>
			<Field>
				<FieldLabel>角色</FieldLabel>
				<div class="grid gap-2 sm:grid-cols-2">
					{#each assignableRoles as role (role.id)}
						<label class="flex items-center gap-2 text-sm">
							<Checkbox
								checked={(editing.roleIds ?? []).includes(role.id)}
								onCheckedChange={(v: boolean | 'indeterminate') => toggleRole(role.id, v === true)}
							/>
							<span>{role.name}</span>
							<code class="text-muted-foreground text-xs">{role.code}</code>
						</label>
					{/each}
				</div>
			</Field>
			<Field>
				<FieldLabel for="remark">备注</FieldLabel>
				<Input id="remark" bind:value={editing.remark} />
			</Field>
		</FieldGroup>
	{/if}
</FormDialog>

<FormDialog
	bind:open={exportOpen}
	title={exportKind === 'xlsx' ? '导出 Excel' : '导出 CSV'}
	description="按当前筛选条件导出,可勾选需要的列"
	submitText="开始导出"
	onSubmit={doExport}
>
	<div class="grid gap-2 sm:grid-cols-2">
		{#each EXPORT_FIELDS as f (f.key)}
			<label class="flex items-center gap-2 text-sm">
				<Checkbox
					checked={exportFields.includes(f.key)}
					onCheckedChange={(v: boolean | 'indeterminate') => toggleExportField(f.key, v === true)}
				/>
				<span>{f.label}</span>
				<code class="text-muted-foreground text-xs">{f.key}</code>
			</label>
		{/each}
	</div>
</FormDialog>

<FormDialog bind:open={resetOpen} title="重置密码" submitText="确认重置" onSubmit={saveReset}>
	<p class="text-muted-foreground text-sm">将重置 {resetTarget?.username} 的登录密码。{PASSWORD_HINT}</p>
	<Field>
		<FieldLabel for="newPassword">新密码</FieldLabel>
		<Input id="newPassword" type="password" bind:value={resetPassword} />
	</Field>
</FormDialog>

<FormDialog bind:open={detailOpen} title={detail ? `${detail.nickname}（${detail.username}）` : '用户详情'} submitText="关闭" onSubmit={() => {}}>
	{#if detail}
		<dl class="grid grid-cols-[5rem_1fr] gap-y-2 text-sm">
			<dt class="text-muted-foreground">状态</dt>
			<dd>{statusLabel[detail.status] ?? '未知'}{detail.pwdReset === 1 ? ' · 待改密' : ''}</dd>
			<dt class="text-muted-foreground">部门</dt>
			<dd>{detail.deptName ?? '未分配'}</dd>
			<dt class="text-muted-foreground">角色</dt>
			<dd>{detail.roleNames?.length ? detail.roleNames.join('、') : '无'}</dd>
			<dt class="text-muted-foreground">邮箱</dt>
			<dd>{detail.email || '-'}</dd>
			<dt class="text-muted-foreground">手机</dt>
			<dd>{detail.phone || '-'}</dd>
			<dt class="text-muted-foreground">地区</dt>
			<dd>{[detail.province, detail.city, detail.district].filter(Boolean).join(' / ') || '-'}</dd>
			<dt class="text-muted-foreground">备注</dt>
			<dd>{detail.remark || '-'}</dd>
		</dl>
	{/if}
</FormDialog>
