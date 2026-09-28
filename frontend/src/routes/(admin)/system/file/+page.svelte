<script lang="ts">
	import { onMount } from 'svelte';
	import * as api from '$lib/api/file';
	import CrudTable, { type CrudColumn } from '$lib/components/crud-table.svelte';
	import AuthImage from '$lib/components/auth-image.svelte';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { DEFAULT_PAGE_SIZE } from '$lib/config';
	import { authStore } from '$lib/stores/auth.svelte';
	import type { StoredFile } from '$lib/types/entities';
	import { toast } from 'svelte-sonner';

	let loading = $state(false);
	let error = $state<string | null>(null);
	let rows = $state<StoredFile[]>([]);
	let search = $state('');
	let searchTimer: ReturnType<typeof setTimeout>;
	let category = $state('');
	let pageNum = $state(1);
	let pageSize = $state(DEFAULT_PAGE_SIZE);
	let total = $state(0);
	let preview = $state<StoredFile | null>(null);

	const canDelete = $derived(authStore.hasPermission('system:file:delete'));
	const canUpload = $derived(authStore.hasPermission('system:file:view'));

	async function load() {
		loading = true;
		error = null;
		try {
			const res = await api.getFileList({
				pageNum,
				pageSize,
				keyword: search || undefined,
				category: category || undefined
			});
			rows = res.records;
			total = res.total;
		} catch (e) {
			error = e instanceof Error ? e.message : '加载失败';
		} finally {
			loading = false;
		}
	}

	onMount(load);

	async function onUpload(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		if (!file) return;
		try {
			await api.uploadFile(file, category || 'default');
			toast.success('已上传');
			await load();
		} catch (err) {
			toast.error(err instanceof Error ? err.message : '上传失败');
		} finally {
			input.value = '';
		}
	}

	async function remove(row: StoredFile) {
		await api.removeFile(row.id);
		toast.success('已删除');
		await load();
	}

	function isImage(row: StoredFile) {
		return (row.contentType ?? '').startsWith('image/') || /\.(png|jpe?g|gif|webp)$/i.test(row.name);
	}

	const columns: CrudColumn<StoredFile>[] = [
		{ header: '文件名', accessor: (r) => r.name },
		{ header: '分类', accessor: (r) => r.category ?? '-', class: 'w-24' },
		{ header: '大小', accessor: (r) => `${Math.ceil((r.size || 0) / 1024)} KB`, class: 'w-24' },
		{ header: '存储', accessor: (r) => r.storageType ?? 'local', class: 'w-20' },
		{ header: '时间', accessor: (r) => r.createTime ?? '', class: 'w-40' }
	];
</script>

<div class="flex flex-col gap-4 px-4 lg:px-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">文件管理</h2>
			<p class="text-muted-foreground text-sm">本地/对象存储上传、预览与删除</p>
		</div>
		<div class="flex items-center gap-2">
			<Input class="w-32" placeholder="分类" bind:value={category} onchange={() => { pageNum = 1; load(); }} />
			{#if canUpload}
				<Button variant="outline" size="sm" class="relative">
					上传
					<input type="file" class="absolute inset-0 cursor-pointer opacity-0" onchange={onUpload} aria-label="上传文件" />
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
		onSearchChange={(v) => {
			search = v;
			pageNum = 1;
			clearTimeout(searchTimer);
			searchTimer = setTimeout(() => load(), 200);
		}}
		searchPlaceholder="搜索文件名"
		onDelete={canDelete ? remove : undefined}
		extraActions={[
			{
				label: '预览/下载',
				onClick: async (r) => {
					if (isImage(r)) preview = r;
					else await api.downloadStoredFile(r.storedName ?? '', r.name);
				}
			}
		]}
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

<Dialog.Root
	open={preview !== null}
	onOpenChange={(o) => {
		if (!o) preview = null;
	}}
>
	<Dialog.Content class="sm:max-w-[640px]">
		<Dialog.Header>
			<Dialog.Title>{preview?.name ?? '预览'}</Dialog.Title>
		</Dialog.Header>
		{#if preview}
			<AuthImage src={preview.url} alt={preview.name} class="max-h-[70vh] max-w-full object-contain" />
		{/if}
		<Dialog.Footer>
			<Button
				variant="outline"
				size="sm"
				onclick={() => preview && api.downloadStoredFile(preview.storedName ?? '', preview.name)}
			>
				下载
			</Button>
			<Button size="sm" onclick={() => (preview = null)}>关闭</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
