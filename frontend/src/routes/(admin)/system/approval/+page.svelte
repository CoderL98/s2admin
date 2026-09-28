<script lang="ts">
	import { onMount, untrack } from 'svelte';
	import * as api from '$lib/api/approval';
	import * as flowApi from '$lib/api/flow';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import * as Tabs from '$lib/components/ui/tabs/index.js';
	import { Field, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { badge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { ApprovalItem, FlowDef } from '$lib/types/entities';
	import { toast } from 'svelte-sonner';

	let tab = $state('pending');
	let loading = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<ApprovalItem[]>([]);
	let search = $state('');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);
	let flows = $state<FlowDef[]>([]);

	let submitOpen = $state(false);
	let flowId = $state('');
	let title = $state('');
	let content = $state('');
	let submitError = $state<string | null>(null);
	let submitting = $state(false);

	let detail = $state<ApprovalItem | null>(null);
	let detailOpen = $state(false);
	let comment = $state('');
	let acting = $state(false);

	const canApply = $derived(authStore.hasPermission('system:approval:apply'));
	const canHandle = $derived(authStore.hasPermission('system:approval:handle'));

	const statusText: Record<number, string> = { 1: '审批中', 2: '已通过', 3: '已驳回', 4: '已撤回' };
	const actionText: Record<string, string> = {
		submit: '提交',
		approve: '通过',
		reject: '驳回',
		cancel: '撤回'
	};

	async function load() {
		loading = true;
		error = null;
		try {
			const query = { pageNum, pageSize, keyword: search || undefined };
			const res = tab === 'pending' ? await api.getPendingApprovals(query) : await api.getMyApprovals(query);
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

	let seenTab = '';

	onMount(async () => {
		if (!canHandle) tab = 'mine';
		await load();
		if (canApply) flows = await flowApi.getFlowOptions().catch(() => []);
	});

	$effect(() => {
		const current = tab;
		untrack(() => {
			if (seenTab && seenTab !== current) {
				pageNum = 1;
				load();
			}
			seenTab = current;
		});
	});

	async function submit() {
		submitError = null;
		if (!flowId || !title.trim()) {
			submitError = '请选择流程并填写标题';
			throw new Error(submitError);
		}
		submitting = true;
		try {
			await api.submitApproval({ flowId: Number(flowId), title: title.trim(), content });
			toast.success('已提交');
			tab = 'mine';
			pageNum = 1;
			await load();
		} catch (e) {
			submitError = e instanceof Error ? e.message : '提交失败';
			throw e;
		} finally {
			submitting = false;
		}
	}

	async function openDetail(row: ApprovalItem) {
		detail = await api.getApproval(row.id);
		comment = '';
		detailOpen = true;
	}

	async function act(kind: 'approve' | 'reject' | 'cancel') {
		if (!detail) return;
		acting = true;
		try {
			if (kind === 'approve') detail = await api.approveApproval(detail.id, comment);
			else if (kind === 'reject') detail = await api.rejectApproval(detail.id, comment);
			else detail = await api.cancelApproval(detail.id, comment);
			toast.success('已处理');
			await load();
		} catch (e) {
			toast.error(e instanceof Error ? e.message : '操作失败');
		} finally {
			acting = false;
		}
	}

	const columns: CrudColumn<ApprovalItem>[] = [
		{ header: '标题', accessor: (r) => r.title },
		{ header: '流程', accessor: (r) => r.flowName ?? '-', class: 'w-32' },
		{ header: '申请人', accessor: (r) => r.applicantName ?? '-', class: 'w-28' },
		{
			header: '状态',
			accessor: (r) => r.status,
			cell: (r) => badge(statusText[r.status] ?? '未知', r.status === 2 ? 'success' : r.status === 1 ? 'warning' : 'outline'),
			class: 'w-24'
		},
		{ header: '当前节点', accessor: (r) => r.currentNodeName ?? '-', class: 'w-32' },
		{ header: '时间', accessor: (r) => r.createTime ?? '', class: 'w-40' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex items-center justify-between gap-3">
		<div>
			<h2 class="text-lg font-semibold">审批中心</h2>
			<p class="text-muted-foreground text-sm">提交申请、处理待办。审批人不能处理自己发起的单据。</p>
		</div>
		{#if canApply}
			<Button onclick={() => { title = ''; content = ''; flowId = flows[0] ? String(flows[0].id) : ''; submitError = null; submitOpen = true; }}>
				发起审批
			</Button>
		{/if}
	</div>

	<Tabs.Root bind:value={tab}>
		<Tabs.List>
			{#if canHandle}<Tabs.Trigger value="pending">待我审批</Tabs.Trigger>{/if}
			<Tabs.Trigger value="mine">我发起的</Tabs.Trigger>
		</Tabs.List>
		<Tabs.Content value={tab} class="pt-4">
			<CrudTable
				data={rows}
				{columns}
				{loading}
				{error}
				searchValue={search}
				onSearchChange={onSearch}
				searchPlaceholder="搜索标题或申请人"
				extraActions={[{ label: '查看', onClick: openDetail }]}
				{pageNum}
				{pageSize}
				{total}
				onPageChange={(p, s) => {
					pageNum = p;
					pageSize = s;
					load();
				}}
			/>
		</Tabs.Content>
	</Tabs.Root>
</div>

<FormDialog bind:open={submitOpen} title="发起审批" submitText="提交" loading={submitting} onSubmit={submit}>
	{#if submitError}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{submitError}</div>
	{/if}
	<div class="flex flex-col gap-3">
		<Field>
			<FieldLabel>流程</FieldLabel>
			<Select.Root type="single" bind:value={flowId}>
				<Select.Trigger>{flows.find((flow) => String(flow.id) === flowId)?.name ?? '请选择'}</Select.Trigger>
				<Select.Content>
					{#each flows as flow (flow.id)}
						<Select.Item value={String(flow.id)}>{flow.name}</Select.Item>
					{/each}
				</Select.Content>
			</Select.Root>
		</Field>
		<Field>
			<FieldLabel for="approval-title">标题</FieldLabel>
			<Input id="approval-title" bind:value={title} />
		</Field>
		<Field>
			<FieldLabel for="approval-content">内容</FieldLabel>
			<textarea id="approval-content" class="border-input bg-background min-h-24 w-full rounded-md border px-3 py-2 text-sm" bind:value={content}></textarea>
		</Field>
	</div>
</FormDialog>

<FormDialog bind:open={detailOpen} title={detail?.title ?? '审批详情'} submitText="关闭" onSubmit={() => {}}>
	{#if detail}
		<div class="flex flex-col gap-3 text-sm">
			<p class="text-muted-foreground">
				{detail.flowName} · {detail.applicantName} · {statusText[detail.status]}
				{#if detail.status === 1}
					· 第 {detail.currentSeq} 步 {detail.currentNodeName}{detail.currentRoleName ? `（${detail.currentRoleName}）` : ''}
				{/if}
			</p>
			{#if detail.content}
				<p class="bg-muted rounded-md px-3 py-2 whitespace-pre-wrap">{detail.content}</p>
			{/if}
			<ol class="flex flex-col gap-2">
				{#each detail.records ?? [] as record (record.id)}
					<li class="border-border rounded-md border px-3 py-2">
						<div class="flex justify-between gap-2">
							<span>{actionText[record.action] ?? record.action} · {record.operatorName}</span>
							<span class="text-muted-foreground">{record.createTime ?? ''}</span>
						</div>
						{#if record.comment}<p class="mt-1 whitespace-pre-wrap">{record.comment}</p>{/if}
					</li>
				{/each}
			</ol>
			{#if detail.canHandle && canHandle}
				<textarea class="border-input bg-background min-h-20 w-full rounded-md border px-3 py-2 text-sm" placeholder="审批意见,驳回时必填" bind:value={comment}></textarea>
				<div class="flex gap-2">
					<Button disabled={acting} onclick={() => act('approve')}>通过</Button>
					<Button variant="destructive" disabled={acting} onclick={() => act('reject')}>驳回</Button>
				</div>
			{:else if detail.status === 1 && detail.applicantId === authStore.currentUser?.id && canApply}
				<textarea class="border-input bg-background min-h-20 w-full rounded-md border px-3 py-2 text-sm" placeholder="撤回说明" bind:value={comment}></textarea>
				<Button variant="outline" disabled={acting} onclick={() => act('cancel')}>撤回</Button>
			{/if}
		</div>
	{/if}
</FormDialog>
