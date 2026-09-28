<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/message';
	import * as userApi from '$lib/api/user';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import FormDialog from '$lib/components/form-dialog.svelte';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Checkbox } from '$lib/components/ui/checkbox/index.js';
	import { badge } from '$lib/components/badges';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { InboxMessage, User } from '$lib/types/entities';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<InboxMessage[]>([]);
	let search = $state('');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);
	let sendOpen = $state(false);
	let title = $state('');
	let content = $state('');
	let users = $state<User[]>([]);
	let receiverIds = $state<number[]>([]);
	let sending = $state(false);

	const canSend = $derived(authStore.hasPermission('system:message:send'));

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await api.getMyMessages({ pageNum, pageSize, keyword: search || undefined });
			rows = res.records;
			total = res.total;
		} catch (e) {
			error = e instanceof Error ? e.message : '加载失败';
		} finally {
			loading = false;
		}
	}

	onMount(async () => {
		await load();
		if (canSend) {
			try {
				users = (await userApi.getUserList({ pageNum: 1, pageSize: 200 })).records;
			} catch (e) {
				toast.error(e instanceof Error ? e.message : '接收人加载失败,请确认有用户查看权限');
			}
		}
	});

	let searchTimer: ReturnType<typeof setTimeout>;
	function onSearch(v: string) {
		search = v;
		clearTimeout(searchTimer);
		searchTimer = setTimeout(() => {
			pageNum = 1;
			load();
		}, 200);
	}

	async function openSend() {
		title = '';
		content = '';
		receiverIds = [];
		sendOpen = true;
	}

	async function send() {
		if (!title.trim() || receiverIds.length === 0) {
			throw new Error('请填写标题并选择接收人');
		}
		sending = true;
		try {
			await api.sendMessage(title.trim(), content, receiverIds);
			toast.success('已发送');
		} finally {
			sending = false;
		}
	}

	async function readOne(row: InboxMessage) {
		if (row.readFlag === 0) {
			await api.markMessageRead(row.id);
			await load();
		}
	}

	async function readAll() {
		await api.markAllMessagesRead();
		toast.success('已全部标为已读');
		await load();
	}

	async function remove(row: InboxMessage) {
		await api.deleteMessage(row.id);
		toast.success('已删除');
		await load();
	}

	function toggleUser(id: number, checked: boolean) {
		receiverIds = checked ? [...receiverIds, id] : receiverIds.filter((x) => x !== id);
	}

	const columns: CrudColumn<InboxMessage>[] = [
		{ header: '标题', accessor: (r) => r.title },
		{ header: '发件人', accessor: (r) => r.senderName ?? '-', class: 'w-32' },
		{
			header: '状态',
			accessor: (r) => r.readFlag,
			cell: (r) => (r.readFlag === 1 ? badge('已读', 'outline') : badge('未读', 'warning')),
			class: 'w-20'
		},
		{ header: '时间', accessor: (r) => r.createTime ?? '', class: 'w-40' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">站内信</h2>
			<p class="text-muted-foreground text-sm">查看发给自己的消息</p>
		</div>
		<div class="flex gap-2">
			<Button variant="outline" size="sm" onclick={readAll}>全部已读</Button>
			{#if canSend}
				<Button size="sm" onclick={openSend}>发信</Button>
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
		searchPlaceholder="搜索"
		extraActions={[{ label: '查看/已读', onClick: readOne }]}
		onDelete={remove}
		deleteConfirmText={(r) => `删除站内信「${r.title}」?`}
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

<FormDialog bind:open={sendOpen} title="发送站内信" submitText="发送" loading={sending} onSubmit={send}>
	<FieldGroup>
		<Field>
			<FieldLabel>标题</FieldLabel>
			<Input bind:value={title} />
		</Field>
		<Field>
			<FieldLabel>内容</FieldLabel>
			<textarea class="border-input bg-background min-h-24 w-full rounded-md border px-3 py-2 text-sm" bind:value={content}
			></textarea>
		</Field>
		<Field>
			<FieldLabel>接收人</FieldLabel>
			<div class="grid max-h-48 gap-2 overflow-auto sm:grid-cols-2">
				{#each users as u (u.id)}
					<label class="flex items-center gap-2 text-sm">
						<Checkbox
							checked={receiverIds.includes(u.id)}
							onCheckedChange={(v: boolean | 'indeterminate') => toggleUser(u.id, v === true)}
						/>
						<span>{u.nickname} ({u.username})</span>
					</label>
				{/each}
			</div>
		</Field>
	</FieldGroup>
</FormDialog>
