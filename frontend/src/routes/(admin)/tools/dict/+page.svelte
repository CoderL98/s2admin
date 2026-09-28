<script lang="ts">
	import * as api from '$lib/api/dict';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import * as Tabs from '$lib/components/ui/tabs/index.js';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { commonStatusBadge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { DictType, DictData } from '$lib/types/entities';
	import { toast } from 'svelte-sonner';
	import { onMount, untrack } from 'svelte';

	const canEdit = $derived(authStore.hasPermission('tools:dict:edit'));
	let tab = $state('type');

	// ========== 字典类型 ==========
	let typeLoading = $state(false);
	let typeSaving = $state(false);
	let types = $state<DictType[]>([]);
	let typeSearch = $state('');
	let typeDialogOpen = $state(false);
	let editingType = $state<Partial<DictType> | null>(null);
	let typeError = $state<string | null>(null);
	let selectedTypeId = $state<number>(1);
	let selectedTypeLabel = $state('');
	let typePageNum = $state(1);
	let typePageSize = $state(DEFAULT_PAGE_SIZE);
	let typeTotal = $state(0);
	let dataPageNum = $state(1);
	let dataPageSize = $state(DEFAULT_PAGE_SIZE);
	let dataTotal = $state(0);
	let typeLoadError = $state<string | null>(null);
	let dataLoadError = $state<string | null>(null);

	async function loadTypes() {
		typeLoading = true;
		typeLoadError = null;
		try {
			const res = await api.getDictTypeList({
				pageNum: typePageNum,
				pageSize: typePageSize,
				keyword: typeSearch || undefined
			});
			types = res.records;
			typeTotal = res.total;
			const current = types.find((t) => t.id === selectedTypeId);
			if (current) {
				selectedTypeLabel = current.name;
			} else if (!selectedTypeLabel && types.length) {
				selectedTypeId = types[0].id;
				selectedTypeLabel = types[0].name;
			}
		} catch (e) {
			typeLoadError = e instanceof Error ? e.message : '加载失败';
		} finally {
			typeLoading = false;
		}
	}

	let typeTimer: ReturnType<typeof setTimeout>;
	function onTypeSearch(v: string) {
		typeSearch = v;
		clearTimeout(typeTimer);
		typeTimer = setTimeout(() => {
			typePageNum = 1;
			loadTypes();
		}, 200);
	}

	onMount(loadTypes);

	function addType() {
		editingType = { status: 0 };
		typeError = null;
		typeDialogOpen = true;
	}

	function editType(t: DictType) {
		editingType = { ...t };
		typeError = null;
		typeDialogOpen = true;
	}

	async function saveType() {
		if (!editingType) return;
		typeError = null;
		if (!editingType.name?.trim() || !editingType.code?.trim()) {
			typeError = '名称和编码不能为空';
			throw new Error(typeError);
		}
		typeSaving = true;
		try {
			if (editingType.id) {
				await api.updateDictType(editingType.id, editingType);
			} else {
				const created = await api.createDictType({
					name: editingType.name!,
					code: editingType.code!,
					status: (editingType.status ?? 0) as DictType['status'],
					remark: editingType.remark
				} as Omit<DictType, 'id' | 'createTime'>);
				selectedTypeId = created.id;
			}
			toast.success('字典类型已保存');
			await loadTypes();
		} catch (e) {
			typeError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			typeSaving = false;
		}
	}

	async function removeType(t: DictType) {
		await api.removeDictType(t.id);
		await loadTypes();
	}

	// ========== 字典数据 ==========
	let dataLoading = $state(false);
	let dataSaving = $state(false);
	let datas = $state<DictData[]>([]);
	let dataSearch = $state('');
	let dataDialogOpen = $state(false);
	let editingData = $state<Partial<DictData> | null>(null);
	let dataError = $state<string | null>(null);

	async function loadData() {
		if (!selectedTypeId) return;
		dataLoading = true;
		dataLoadError = null;
		try {
			const res = await api.getDictDataList({
				pageNum: dataPageNum,
				pageSize: dataPageSize,
				dictTypeId: selectedTypeId,
				keyword: dataSearch || undefined
			});
			datas = res.records;
			dataTotal = res.total;
		} catch (e) {
			dataLoadError = e instanceof Error ? e.message : '加载失败';
		} finally {
			dataLoading = false;
		}
	}

	$effect(() => {
		const typeId = selectedTypeId;
		untrack(() => {
			if (!typeId) return;
			dataPageNum = 1;
			loadData();
		});
	});

	let dataTimer: ReturnType<typeof setTimeout>;
	function onDataSearch(v: string) {
		dataSearch = v;
		clearTimeout(dataTimer);
		dataTimer = setTimeout(() => {
			dataPageNum = 1;
			loadData();
		}, 200);
	}

	function addData() {
		editingData = { status: 0, dictTypeId: selectedTypeId, sort: 0 };
		dataError = null;
		dataDialogOpen = true;
	}

	function editData(d: DictData) {
		editingData = { ...d };
		dataError = null;
		dataDialogOpen = true;
	}

	async function saveData() {
		if (!editingData) return;
		dataError = null;
		if (!editingData.label?.trim() || !editingData.value?.trim()) {
			dataError = '标签和值不能为空';
			throw new Error(dataError);
		}
		dataSaving = true;
		try {
			if (editingData.id) {
				await api.updateDictData(editingData.id, editingData);
			} else {
				await api.createDictData({
					dictTypeId: editingData.dictTypeId ?? selectedTypeId,
					label: editingData.label!,
					value: editingData.value!,
					sort: editingData.sort ?? 0,
					status: (editingData.status ?? 0) as DictData['status'],
					remark: editingData.remark
				} as Omit<DictData, 'id' | 'createTime'>);
			}
			toast.success('字典数据已保存');
			await loadData();
		} catch (e) {
			dataError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			dataSaving = false;
		}
	}

	async function removeData(d: DictData) {
		await api.removeDictData(d.id);
		await loadData();
	}

	const typeColumns: CrudColumn<DictType>[] = [
		{ header: 'ID', accessor: (r) => r.id, class: 'w-16' },
		{ header: '字典名称', accessor: (r) => r.name },
		{ header: '字典编码', accessor: (r) => r.code, cell: (r) => ({ kind: 'code', text: r.code }) },
		{ header: '状态', accessor: (r) => r.status, cell: (r) => commonStatusBadge(r.status), class: 'w-20' },
		{ header: '备注', accessor: (r) => r.remark ?? '-' }
	];

	const dataColumns: CrudColumn<DictData>[] = [
		{ header: 'ID', accessor: (r) => r.id, class: 'w-16' },
		{ header: '字典标签', accessor: (r) => r.label },
		{ header: '字典值', accessor: (r) => r.value, cell: (r) => ({ kind: 'code', text: r.value }) },
		{ header: '排序', accessor: (r) => r.sort, class: 'w-16', align: 'center' },
		{ header: '状态', accessor: (r) => r.status, cell: (r) => commonStatusBadge(r.status), class: 'w-20' },
		{ header: '备注', accessor: (r) => r.remark ?? '-' }
	];

	const currentTypeName = $derived(types.find((t) => t.id === selectedTypeId)?.name ?? selectedTypeLabel);
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div>
		<h2 class="text-lg font-semibold">字典管理</h2>
		<p class="text-muted-foreground text-sm">维护系统数据字典(枚举值)</p>
	</div>

	<Tabs.Root bind:value={tab} class="w-full">
		<Tabs.List>
			<Tabs.Trigger value="type">字典类型</Tabs.Trigger>
			<Tabs.Trigger value="data">字典数据 - {currentTypeName || '(未选)'}</Tabs.Trigger>
		</Tabs.List>

		<Tabs.Content value="type" class="mt-4">
			<CrudTable
				data={types}
				columns={typeColumns}
				loading={typeLoading}
				error={typeLoadError}
				searchValue={typeSearch}
				onSearchChange={onTypeSearch}
				searchPlaceholder="搜索字典名称或编码"
				addLabel="新增字典类型"
				onAdd={canEdit ? addType : undefined}
				onEdit={canEdit ? editType : undefined}
				onDelete={canEdit ? removeType : undefined}
				pageNum={typePageNum}
				pageSize={typePageSize}
				total={typeTotal}
				onPageChange={(p, s) => {
					typePageNum = p;
					typePageSize = s;
					loadTypes();
				}}
			/>
		</Tabs.Content>

		<Tabs.Content value="data" class="mt-4">
			<div class="mb-3 flex items-center gap-2">
				<span class="text-sm">当前字典类型:</span>
				<Select.Root type="single" bind:value={
					() => String(selectedTypeId),
					(v) => {
						selectedTypeId = Number(v);
						selectedTypeLabel = types.find((t) => t.id === selectedTypeId)?.name ?? selectedTypeLabel;
					}
				}>
					<Select.Trigger class="w-48">
						{currentTypeName || '请选择'}
					</Select.Trigger>
					<Select.Content>
						{#each types as t (t.id)}
							<Select.Item value={String(t.id)}>{t.name} ({t.code})</Select.Item>
						{/each}
					</Select.Content>
				</Select.Root>
			</div>
			<CrudTable
				data={datas}
				columns={dataColumns}
				loading={dataLoading}
				error={dataLoadError}
				searchValue={dataSearch}
				onSearchChange={onDataSearch}
				searchPlaceholder="搜索字典标签或值"
				addLabel="新增字典数据"
				onAdd={canEdit ? addData : undefined}
				onEdit={canEdit ? editData : undefined}
				onDelete={canEdit ? removeData : undefined}
				pageNum={dataPageNum}
				pageSize={dataPageSize}
				total={dataTotal}
				onPageChange={(p, s) => {
					dataPageNum = p;
					dataPageSize = s;
					loadData();
				}}
			/>
		</Tabs.Content>
	</Tabs.Root>
</div>

<!-- 字典类型编辑 -->
<FormDialog
	bind:open={typeDialogOpen}
	title={editingType?.id ? '编辑字典类型' : '新增字典类型'}
	submitText="保存"
	loading={typeSaving}
	onSubmit={saveType}
>
	{#if typeError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{typeError}</div>
	{/if}
	{#if editingType}
		<FieldGroup>
			<Field>
				<FieldLabel for="t-name">字典名称 *</FieldLabel>
				<Input id="t-name" bind:value={editingType.name} />
			</Field>
			<Field>
				<FieldLabel for="t-code">字典编码 *</FieldLabel>
				<Input id="t-code" bind:value={editingType.code} placeholder="snake_case" disabled={!!editingType.id} />
			</Field>
			<Field>
				<FieldLabel for="t-status">状态</FieldLabel>
				<Select.Root type="single" bind:value={
					() => String(editingType!.status ?? 0),
					(v) => (editingType!.status = Number(v) as DictType['status'])
				}>
					<Select.Trigger id="t-status">{['正常', '停用'][editingType.status ?? 0]}</Select.Trigger>
					<Select.Content>
						<Select.Item value="0">正常</Select.Item>
						<Select.Item value="1">停用</Select.Item>
					</Select.Content>
				</Select.Root>
			</Field>
			<Field>
				<FieldLabel for="t-remark">备注</FieldLabel>
				<Input id="t-remark" bind:value={editingType.remark} />
			</Field>
		</FieldGroup>
	{/if}
</FormDialog>

<!-- 字典数据编辑 -->
<FormDialog
	bind:open={dataDialogOpen}
	title={editingData?.id ? '编辑字典数据' : '新增字典数据'}
	submitText="保存"
	loading={dataSaving}
	onSubmit={saveData}
>
	{#if dataError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{dataError}</div>
	{/if}
	{#if editingData}
		<FieldGroup>
			<Field>
				<FieldLabel for="d-label">字典标签 *</FieldLabel>
				<Input id="d-label" bind:value={editingData.label} />
			</Field>
			<Field>
				<FieldLabel for="d-value">字典值 *</FieldLabel>
				<Input id="d-value" bind:value={editingData.value} />
			</Field>
			<div class="grid grid-cols-2 gap-3">
				<Field>
					<FieldLabel for="d-sort">排序</FieldLabel>
					<Input id="d-sort" type="number" bind:value={editingData.sort} />
				</Field>
				<Field>
					<FieldLabel for="d-status">状态</FieldLabel>
					<Select.Root type="single" bind:value={
						() => String(editingData!.status ?? 0),
						(v) => (editingData!.status = Number(v) as DictData['status'])
					}>
						<Select.Trigger id="d-status">{['正常', '停用'][editingData.status ?? 0]}</Select.Trigger>
						<Select.Content>
							<Select.Item value="0">正常</Select.Item>
							<Select.Item value="1">停用</Select.Item>
						</Select.Content>
					</Select.Root>
				</Field>
			</div>
			<Field>
				<FieldLabel for="d-remark">备注</FieldLabel>
				<Input id="d-remark" bind:value={editingData.remark} />
			</Field>
		</FieldGroup>
	{/if}
</FormDialog>