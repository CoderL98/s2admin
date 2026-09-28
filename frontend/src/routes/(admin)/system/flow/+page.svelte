<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/flow';
	import * as roleApi from '$lib/api/role';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { commonStatusBadge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { FlowDef, Role } from '$lib/types/entities';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<FlowDef[]>([]);
	let search = $state('');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);
	let roles = $state<Role[]>([]);

	let dialogOpen = $state(false);
	let editing = $state<Partial<FlowDef> | null>(null);
	type NodeDraft = {
		name: string;
		roleId?: number;
		nodeType: number;
		signMode: number;
		rejectTo: string;
		conditionExpr: string;
		yesSeq: string;
		noSeq: string;
	};
	let nodes = $state<NodeDraft[]>([]);

	function blankNode(): NodeDraft {
		return {
			name: '审批',
			roleId: roles[0]?.id,
			nodeType: 1,
			signMode: 1,
			rejectTo: '',
			conditionExpr: '',
			yesSeq: '',
			noSeq: ''
		};
	}
	let formError = $state<string | null>(null);

	const canAdd = $derived(authStore.hasPermission('system:flow:add'));
	const canEdit = $derived(authStore.hasPermission('system:flow:edit'));
	const canDelete = $derived(authStore.hasPermission('system:flow:delete'));

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await api.getFlowList({ pageNum, pageSize, keyword: search || undefined });
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

	onMount(async () => {
		await load();
		roles = (await roleApi.getAllRoles().catch(() => [])).filter((role) => role.status === 0 && role.code !== 'SUPER_ADMIN');
	});

	function openAdd() {
		editing = { status: 0, code: '', name: '' };
		nodes = [blankNode()];
		formError = null;
		dialogOpen = true;
	}

	async function openEdit(row: FlowDef) {
		const detail = await api.getFlow(row.id);
		editing = { ...detail };
		nodes = (detail.nodes ?? []).map((node) => ({
			name: node.name,
			roleId: node.roleId || undefined,
			nodeType: node.nodeType ?? 1,
			signMode: node.signMode ?? 1,
			rejectTo: node.rejectTo == null ? '' : String(node.rejectTo),
			conditionExpr: node.conditionExpr ?? '',
			yesSeq: node.yesSeq == null ? '' : String(node.yesSeq),
			noSeq: node.noSeq == null ? '' : String(node.noSeq)
		}));
		if (!nodes.length) nodes = [blankNode()];
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
		if (!nodes.length || nodes.some((node) => !node.name.trim())) {
			formError = '每个节点都要填写名称';
			throw new Error(formError);
		}
		if (nodes.some((node) => node.nodeType !== 2 && !node.roleId)) {
			formError = '审批节点要选择角色';
			throw new Error(formError);
		}
		if (nodes.some((node) => node.nodeType === 2 && (!node.conditionExpr.trim() || node.yesSeq === '' || node.noSeq === ''))) {
			formError = '条件节点要填写条件和走向。序号从 1 开始,0 表示结束并通过';
			throw new Error(formError);
		}
		if (!nodes.some((node) => node.nodeType !== 2)) {
			formError = '至少保留一个审批节点';
			throw new Error(formError);
		}
		saving = true;
		try {
			const payload = {
				name: editing.name.trim(),
				code: editing.code.trim(),
				status: editing.status ?? 0,
				remark: editing.remark,
				nodes: nodes.map((node) => ({
					name: node.name.trim(),
					nodeType: node.nodeType,
					roleId: node.nodeType === 2 ? undefined : node.roleId,
					signMode: node.signMode,
					rejectTo: node.rejectTo.trim() === '' ? null : Number(node.rejectTo),
					conditionExpr: node.nodeType === 2 ? node.conditionExpr.trim() : undefined,
					yesSeq: node.nodeType === 2 && node.yesSeq.trim() !== '' ? Number(node.yesSeq) : undefined,
					noSeq: node.nodeType === 2 && node.noSeq.trim() !== '' ? Number(node.noSeq) : undefined
				}))
			};
			if (editing.id) await api.updateFlow(editing.id, payload);
			else await api.createFlow(payload);
			toast.success('流程已保存');
			await load();
		} catch (e) {
			formError = e instanceof Error ? e.message : '保存失败';
			throw e;
		} finally {
			saving = false;
		}
	}

	async function remove(row: FlowDef) {
		await api.removeFlow(row.id);
		toast.success('已删除');
		await load();
	}

	const columns: CrudColumn<FlowDef>[] = [
		{ header: '名称', accessor: (r) => r.name },
		{ header: '编码', accessor: (r) => r.code, cell: (r) => ({ kind: 'code', text: r.code }) },
		{ header: '节点数', accessor: (r) => r.nodeCount ?? r.nodes?.length ?? 0, class: 'w-20' },
		{ header: '状态', accessor: (r) => r.status, cell: (r) => commonStatusBadge(r.status), class: 'w-20' },
		{ header: '说明', accessor: (r) => r.remark ?? '-' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div>
		<h2 class="text-lg font-semibold">流程定义</h2>
		<p class="text-muted-foreground text-sm">
			支持或签、会签、条件分支和驳回跳转。条件写成 contains:文本 或 not:contains:文本,序号从 1 开始,0 表示结束并通过。驳回留空即结束,-1 回到上一个审批节点。
		</p>
	</div>
	<CrudTable
		data={rows}
		{columns}
		{loading}
		{error}
		searchValue={search}
		onSearchChange={onSearch}
		searchPlaceholder="搜索流程名称或编码"
		addLabel="新增流程"
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

<FormDialog bind:open={dialogOpen} title={editing?.id ? '编辑流程' : '新增流程'} submitText="保存" loading={saving} onSubmit={save}>
	{#if formError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{formError}</div>
	{/if}
	{#if editing}
		<FieldGroup>
			<div class="grid grid-cols-2 gap-3">
				<Field>
					<FieldLabel for="flow-name">名称 *</FieldLabel>
					<Input id="flow-name" bind:value={editing.name} />
				</Field>
				<Field>
					<FieldLabel for="flow-code">编码 *</FieldLabel>
					<Input id="flow-code" bind:value={editing.code} placeholder="GENERAL" />
				</Field>
			</div>
			<Field>
				<FieldLabel for="flow-remark">说明</FieldLabel>
				<Input id="flow-remark" bind:value={editing.remark} />
			</Field>
			<div class="flex flex-col gap-2">
				<div class="flex items-center justify-between">
					<span class="text-sm font-medium">审批节点</span>
					<Button type="button" variant="outline" size="sm" onclick={() => (nodes = [...nodes, { ...blankNode(), name: '' }])}>
						添加节点
					</Button>
				</div>
				{#each nodes as node, index (index)}
					<div class="flex flex-col gap-2 rounded-md border p-3">
						<div class="text-muted-foreground text-xs">序号 {index + 1}</div>
						<div class="grid grid-cols-2 gap-2">
							<Input placeholder="节点名称" bind:value={node.name} />
							<Select.Root type="single" value={String(node.nodeType)} onValueChange={(v) => (node.nodeType = Number(v))}>
								<Select.Trigger>{node.nodeType === 2 ? '条件' : '审批'}</Select.Trigger>
								<Select.Content>
									<Select.Item value="1">审批</Select.Item>
									<Select.Item value="2">条件</Select.Item>
								</Select.Content>
							</Select.Root>
						</div>
						{#if node.nodeType === 2}
							<Input placeholder="contains:紧急 或 not:contains:紧急" bind:value={node.conditionExpr} />
							<div class="grid grid-cols-2 gap-2">
								<Input placeholder="满足时序号,0=通过" bind:value={node.yesSeq} />
								<Input placeholder="不满足时序号,0=通过" bind:value={node.noSeq} />
							</div>
						{:else}
							<div class="grid grid-cols-2 gap-2">
								<Select.Root
									type="single"
									value={node.roleId ? String(node.roleId) : undefined}
									onValueChange={(v) => (node.roleId = Number(v))}
								>
									<Select.Trigger>{roles.find((role) => role.id === node.roleId)?.name ?? '选择角色'}</Select.Trigger>
									<Select.Content>
										{#each roles as role (role.id)}
											<Select.Item value={String(role.id)}>{role.name}</Select.Item>
										{/each}
									</Select.Content>
								</Select.Root>
								<Select.Root type="single" value={String(node.signMode)} onValueChange={(v) => (node.signMode = Number(v))}>
									<Select.Trigger>{node.signMode === 2 ? '会签' : '或签'}</Select.Trigger>
									<Select.Content>
										<Select.Item value="1">或签(任一人)</Select.Item>
										<Select.Item value="2">会签(都要同意)</Select.Item>
									</Select.Content>
								</Select.Root>
							</div>
							<Input placeholder="驳回目标:空=结束,-1=上一节点,或填写序号" bind:value={node.rejectTo} />
						{/if}
						<Button type="button" variant="ghost" size="sm" disabled={nodes.length === 1} onclick={() => (nodes = nodes.filter((_, i) => i !== index))}>
							移除
						</Button>
					</div>
				{/each}
			</div>
		</FieldGroup>
	{/if}
</FormDialog>
