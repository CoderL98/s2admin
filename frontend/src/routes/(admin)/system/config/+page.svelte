<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/config';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { badge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { SysConfig } from '$lib/types/entities';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<SysConfig[]>([]);
	let search = $state('');
	let groupFilter = $state('__all__');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);

	let dialogOpen = $state(false);
	let editing = $state<Partial<SysConfig> | null>(null);
	let formError = $state<string | null>(null);

	const canAdd = $derived(authStore.hasPermission('system:config:add'));
	const canEdit = $derived(authStore.hasPermission('system:config:edit'));
	const canDelete = $derived(authStore.hasPermission('system:config:delete'));
	let groups = $state<string[]>([]);

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await api.getConfigList({
				pageNum,
				pageSize,
				keyword: search || undefined,
				groupCode: groupFilter && groupFilter !== '__all__' ? groupFilter : undefined
			});
			rows = res.records;
			total = res.total;
			const fromApi = await api.getConfigGroups().catch(() => [] as string[]);
			const extra = rows.map((r) => r.groupCode).filter(Boolean) as string[];
			groups = [...new Set([...fromApi, ...extra])];
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
		editing = { configType: 'string', groupCode: 'default' };
		formError = null;
		dialogOpen = true;
	}

	function openEdit(c: SysConfig) {
		editing = { ...c };
		formError = null;
		dialogOpen = true;
	}

	async function save() {
		if (!editing) return;
		formError = null;
		if (!editing.configKey?.trim() || editing.configValue === undefined) {
			formError = '配置键和配置值不能为空';
			throw new Error(formError);
		}
		saving = true;
		try {
			const payload = {
				configKey: editing.configKey,
				configValue: String(editing.configValue),
				configType: (editing.configType ?? 'string') as SysConfig['configType'],
				groupCode: editing.groupCode ?? 'default',
				remark: editing.remark
			};
			if (editing.id) {
				await api.updateConfig(editing.id, payload);
			} else {
				await api.createConfig(payload);
			}
			toast.success('配置已保存');
			await load();
		} catch (e) {
			formError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			saving = false;
		}
	}

	async function remove(c: SysConfig) {
		await api.removeConfig(c.id);
		toast.success('已删除');
		await load();
	}

	function typeBadge(t: string) {
		if (t === 'boolean') return badge('boolean', 'info');
		if (t === 'number') return badge('number', 'warning');
		return badge('string', 'default');
	}

	const columns: CrudColumn<SysConfig>[] = [
		{ header: 'ID', accessor: (r) => r.id, class: 'w-16' },
		{ header: '配置键', accessor: (r) => r.configKey, cell: (r) => ({ kind: 'code', text: r.configKey }) },
		{ header: '配置值', accessor: (r) => r.configValue },
		{ header: '类型', accessor: (r) => r.configType, cell: (r) => typeBadge(r.configType), class: 'w-24' },
		{ header: '分组', accessor: (r) => r.groupCode, class: 'w-24' },
		{ header: '备注', accessor: (r) => r.remark ?? '-' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">系统配置</h2>
			<p class="text-muted-foreground text-sm">管理系统运行时配置参数</p>
		</div>
		<Select.Root
			type="single"
			bind:value={groupFilter}
			onValueChange={() => {
				pageNum = 1;
				load();
			}}
		>
			<Select.Trigger class="w-36">
				{groupFilter && groupFilter !== '__all__' ? groupFilter : '全部分组'}
			</Select.Trigger>
			<Select.Content>
				<Select.Item value="__all__">全部分组</Select.Item>
				{#each groups as g (g)}
					<Select.Item value={g}>{g}</Select.Item>
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
		searchPlaceholder="搜索配置键或配置值"
		addLabel="新增配置"
		onAdd={canAdd ? openAdd : undefined}
		onEdit={canEdit ? openEdit : undefined}
		onDelete={canDelete ? remove : undefined}
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
	title={editing?.id ? '编辑配置' : '新增配置'}
	submitText="保存"
	loading={saving}
	onSubmit={save}
>
	{#if formError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{formError}</div>
	{/if}
	{#if editing}
		<FieldGroup>
			<Field>
				<FieldLabel for="configKey">配置键 *</FieldLabel>
				<Input id="configKey" bind:value={editing.configKey} placeholder="sys.example.key" disabled={!!editing.id} />
			</Field>
			<Field>
				<FieldLabel for="configValue">配置值 *</FieldLabel>
				<Input id="configValue" bind:value={editing.configValue} />
			</Field>
			<div class="grid grid-cols-2 gap-3">
				<Field>
					<FieldLabel for="configType">类型</FieldLabel>
					<Select.Root
						type="single"
						bind:value={() => editing!.configType ?? 'string', (v) => (editing!.configType = v as SysConfig['configType'])}
					>
						<Select.Trigger id="configType">{editing.configType ?? 'string'}</Select.Trigger>
						<Select.Content>
							<Select.Item value="string">string</Select.Item>
							<Select.Item value="number">number</Select.Item>
							<Select.Item value="boolean">boolean</Select.Item>
						</Select.Content>
					</Select.Root>
				</Field>
				<Field>
					<FieldLabel for="groupCode">分组编码</FieldLabel>
					<Input id="groupCode" bind:value={editing.groupCode} placeholder="已有分组或自定义" list="config-group-options" />
					<datalist id="config-group-options">
						{#each groups as g (g)}
							<option value={g}>{g}</option>
						{/each}
					</datalist>
				</Field>
			</div>
			<Field>
				<FieldLabel for="remark">备注</FieldLabel>
				<Input id="remark" bind:value={editing.remark} />
			</Field>
		</FieldGroup>
	{/if}
</FormDialog>
