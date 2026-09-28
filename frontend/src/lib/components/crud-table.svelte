<script module lang="ts">
	import type { CellView } from '$lib/components/badges';

	export interface CrudColumn<Row> {
		header: string;
		accessor: (row: Row) => unknown;
		cell?: (row: Row) => CellView | string;
		class?: string;
		align?: 'left' | 'center' | 'right';
	}

	export interface CrudExtraAction<Row> {
		label: string;
		onClick: (row: Row) => void | Promise<void>;
		show?: (row: Row) => boolean;
	}

	export interface CrudBatchAction {
		label: string;
		variant?: 'default' | 'destructive' | 'outline';
		onClick: (ids: (number | string)[]) => void | Promise<void>;
	}

	const toneClass: Record<string, string> = {
		default: 'bg-secondary text-secondary-foreground',
		success: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300',
		destructive: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300',
		warning: 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300',
		info: 'bg-sky-100 text-sky-800 dark:bg-sky-900/40 dark:text-sky-300',
		outline: 'border text-foreground'
	};

	function toCell(value: CellView | string | unknown): CellView {
		if (typeof value === 'string') return { kind: 'text', text: value };
		if (value && typeof value === 'object' && 'kind' in value) return value as CellView;
		return { kind: 'text', text: value == null ? '' : String(value) };
	}
</script>

<script lang="ts" generics="T">
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import * as Table from '$lib/components/ui/table/index.js';
	import * as AlertDialog from '$lib/components/ui/alert-dialog/index.js';
	import * as DropdownMenu from '$lib/components/ui/dropdown-menu/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { Checkbox } from '$lib/components/ui/checkbox/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import SearchIcon from '@tabler/icons-svelte/icons/search';
	import PlusIcon from '@tabler/icons-svelte/icons/plus';
	import MoreHorizontalIcon from '@tabler/icons-svelte/icons/dots';
	import PencilIcon from '@tabler/icons-svelte/icons/pencil';
	import TrashIcon from '@tabler/icons-svelte/icons/trash';
	import { toast } from 'svelte-sonner';

	type Row = T & { id: number | string };

	interface Props {
		data: Row[];
		columns: CrudColumn<Row>[];
		loading?: boolean;
		error?: string | null;
		searchPlaceholder?: string;
		searchValue: string;
		onSearchChange: (v: string) => void;
		addLabel?: string;
		onAdd?: () => void;
		onEdit?: (row: Row) => void;
		onDelete?: (row: Row) => void | Promise<void>;
		deleteConfirmText?: (row: Row) => string;
		extraActions?: CrudExtraAction<Row>[];
		hideActions?: boolean;
		emptyText?: string;
		pageNum?: number;
		pageSize?: number;
		total?: number;
		onPageChange?: (pageNum: number, pageSize: number) => void;
		selectable?: boolean;
		selectedIds?: (number | string)[];
		onSelectedChange?: (ids: (number | string)[]) => void;
		batchActions?: CrudBatchAction[];
	}

	let {
		data,
		columns,
		loading = false,
		error = null,
		searchPlaceholder = '搜索...',
		searchValue,
		onSearchChange,
		addLabel,
		onAdd,
		onEdit,
		onDelete,
		deleteConfirmText,
		extraActions = [],
		hideActions = false,
		emptyText = '暂无数据',
		pageNum = 1,
		pageSize = 10,
		total,
		onPageChange,
		selectable = false,
		selectedIds = $bindable<(number | string)[]>([]),
		onSelectedChange,
		batchActions = []
	}: Props = $props();

	let pendingDelete = $state<Row | null>(null);
	let deleting = $state(false);

	const pages = $derived(Math.max(1, Math.ceil((total ?? 0) / Math.max(pageSize, 1))));
	const showActions = $derived(!hideActions && !!(onEdit || onDelete || extraActions.length));
	const selectedSet = $derived(new Set(selectedIds.map(String)));
	const pageIds = $derived(data.map((r) => r.id));
	const allPageSelected = $derived(pageIds.length > 0 && pageIds.every((id) => selectedSet.has(String(id))));
	const somePageSelected = $derived(pageIds.some((id) => selectedSet.has(String(id))));
	const colSpan = $derived(columns.length + (showActions ? 1 : 0) + (selectable ? 1 : 0));

	function setSelected(ids: (number | string)[]) {
		selectedIds = ids;
		onSelectedChange?.(ids);
	}

	function toggleRow(id: number | string, checked: boolean) {
		const key = String(id);
		if (checked) {
			if (selectedSet.has(key)) return;
			setSelected([...selectedIds, id]);
		} else {
			setSelected(selectedIds.filter((x) => String(x) !== key));
		}
	}

	function toggleAll(checked: boolean) {
		if (checked) {
			const extra = pageIds.filter((id) => !selectedSet.has(String(id)));
			setSelected([...selectedIds, ...extra]);
		} else {
			const drop = new Set(pageIds.map(String));
			setSelected(selectedIds.filter((id) => !drop.has(String(id))));
		}
	}

	async function confirmDelete() {
		if (!pendingDelete || !onDelete) return;
		deleting = true;
		try {
			await onDelete(pendingDelete);
			pendingDelete = null;
		} catch (e) {
			toast.error(e instanceof Error ? e.message : '删除失败');
		} finally {
			deleting = false;
		}
	}

	function alignClass(align?: CrudColumn<Row>['align']) {
		if (align === 'center') return 'text-center';
		if (align === 'right') return 'text-right';
		return 'text-left';
	}

	function cellOf(col: CrudColumn<Row>, row: Row): CellView {
		if (col.cell) return toCell(col.cell(row));
		return toCell(col.accessor(row));
	}
</script>

<div class="flex flex-col gap-4">
	<div class="flex items-center justify-between gap-2">
		<div class="relative w-full max-w-sm">
			<SearchIcon class="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
			<Input
				type="search"
				placeholder={searchPlaceholder}
				class="pl-8"
				value={searchValue}
				oninput={(e) => onSearchChange((e.currentTarget as HTMLInputElement).value)}
			/>
		</div>
		<div class="flex flex-wrap items-center gap-2">
			{#if selectable && selectedIds.length > 0}
				<span class="text-muted-foreground text-sm">已选 {selectedIds.length}</span>
				{#each batchActions as action (action.label)}
					<Button
						size="sm"
						variant={action.variant ?? 'outline'}
						onclick={() => action.onClick(selectedIds)}
					>
						{action.label}
					</Button>
				{/each}
			{/if}
			{#if onAdd && addLabel}
				<Button onclick={onAdd} size="sm">
					<PlusIcon />
					<span>{addLabel}</span>
				</Button>
			{/if}
		</div>
	</div>

	{#if error}
		<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{error}</div>
	{/if}

	<div class="overflow-hidden rounded-lg border">
		<Table.Root>
			<Table.Header class="bg-muted">
				<Table.Row>
					{#if selectable}
						<Table.Head class="w-10">
							<Checkbox
								checked={allPageSelected}
								indeterminate={somePageSelected && !allPageSelected}
								onCheckedChange={(v: boolean | 'indeterminate') => toggleAll(v === true)}
								aria-label="全选本页"
							/>
						</Table.Head>
					{/if}
					{#each columns as col, i (i)}
						<Table.Head class="{col.class ?? ''} {alignClass(col.align)}">
							{col.header}
						</Table.Head>
					{/each}
					{#if showActions}
						<Table.Head class="w-16 {alignClass('right')}">操作</Table.Head>
					{/if}
				</Table.Row>
			</Table.Header>
			<Table.Body>
				{#if loading}
					{#each Array(5) as _, i (i)}
						<Table.Row>
							{#each Array(colSpan) as _c, j (j)}
								<Table.Cell><Skeleton class="h-4 w-full" /></Table.Cell>
							{/each}
						</Table.Row>
					{/each}
				{:else if data.length === 0}
					<Table.Row>
						<Table.Cell colspan={colSpan} class="text-muted-foreground h-32 text-center text-sm">
							{emptyText}
						</Table.Cell>
					</Table.Row>
				{:else}
					{#each data as row (row.id)}
						<Table.Row>
							{#if selectable}
								<Table.Cell>
									<Checkbox
										checked={selectedSet.has(String(row.id))}
										onCheckedChange={(v: boolean | 'indeterminate') =>
											toggleRow(row.id, v === true)}
										aria-label={`选择 ${String(row.id)}`}
									/>
								</Table.Cell>
							{/if}
							{#each columns as col, i (i)}
								{@const view = cellOf(col, row)}
								<Table.Cell class="{col.class ?? ''} {alignClass(col.align)}">
									{#if view.kind === 'badge'}
										<span
											class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium {toneClass[
												view.tone ?? 'default'
											]}"
										>
											{view.text}
										</span>
									{:else if view.kind === 'code'}
										<code class="bg-muted rounded px-1.5 py-0.5 text-xs">{view.text}</code>
									{:else}
										{view.text}
									{/if}
								</Table.Cell>
							{/each}
							{#if showActions}
								<Table.Cell class="text-right">
									<DropdownMenu.Root>
										<DropdownMenu.Trigger>
											{#snippet child({ props })}
												<Button variant="ghost" size="icon" {...props} class="size-8">
													<MoreHorizontalIcon class="size-4" />
													<span class="sr-only">操作</span>
												</Button>
											{/snippet}
										</DropdownMenu.Trigger>
										<DropdownMenu.Content align="end">
											{#if onEdit}
												<DropdownMenu.Item onclick={() => onEdit(row)}>
													<PencilIcon class="size-4" />
													<span>编辑</span>
												</DropdownMenu.Item>
											{/if}
											{#each extraActions.filter((action) => !action.show || action.show(row)) as action (action.label)}
												<DropdownMenu.Item
													onclick={async () => {
														try {
															await action.onClick(row);
														} catch (e) {
															toast.error(e instanceof Error ? e.message : '操作失败');
														}
													}}
												>
													<span>{action.label}</span>
												</DropdownMenu.Item>
											{/each}
											{#if onDelete}
												<DropdownMenu.Item
													variant="destructive"
													onclick={() => (pendingDelete = row)}
												>
													<TrashIcon class="size-4" />
													<span>删除</span>
												</DropdownMenu.Item>
											{/if}
										</DropdownMenu.Content>
									</DropdownMenu.Root>
								</Table.Cell>
							{/if}
						</Table.Row>
					{/each}
				{/if}
			</Table.Body>
		</Table.Root>
	</div>

	{#if total !== undefined && onPageChange}
		<div class="flex flex-wrap items-center justify-between gap-3 text-sm">
			<p class="text-muted-foreground">共 {total} 条</p>
			<div class="flex items-center gap-2">
				<Select.Root
					type="single"
					value={String(pageSize)}
					onValueChange={(v) => {
						if (v) onPageChange(1, Number(v));
					}}
				>
					<Select.Trigger class="w-28">{pageSize} 条/页</Select.Trigger>
					<Select.Content>
						{#each [10, 20, 50] as size (size)}
							<Select.Item value={String(size)}>{size} 条/页</Select.Item>
						{/each}
					</Select.Content>
				</Select.Root>
				<Button
					variant="outline"
					size="sm"
					disabled={pageNum <= 1 || loading}
					onclick={() => onPageChange(pageNum - 1, pageSize)}
				>
					上一页
				</Button>
				<span class="text-muted-foreground min-w-16 text-center">{pageNum} / {pages}</span>
				<Button
					variant="outline"
					size="sm"
					disabled={pageNum >= pages || loading}
					onclick={() => onPageChange(pageNum + 1, pageSize)}
				>
					下一页
				</Button>
			</div>
		</div>
	{/if}
</div>

<AlertDialog.Root
	open={pendingDelete !== null}
	onOpenChange={(o) => {
		if (!o) pendingDelete = null;
	}}
>
	<AlertDialog.Content>
		<AlertDialog.Header>
			<AlertDialog.Title>确认删除?</AlertDialog.Title>
			<AlertDialog.Description>
				{pendingDelete && deleteConfirmText
					? deleteConfirmText(pendingDelete)
					: '此操作不可撤销,删除后将无法恢复。'}
			</AlertDialog.Description>
		</AlertDialog.Header>
		<AlertDialog.Footer>
			<AlertDialog.Cancel disabled={deleting}>取消</AlertDialog.Cancel>
			<AlertDialog.Action onclick={confirmDelete} disabled={deleting}>
				{deleting ? '删除中...' : '确认删除'}
			</AlertDialog.Action>
		</AlertDialog.Footer>
	</AlertDialog.Content>
</AlertDialog.Root>
