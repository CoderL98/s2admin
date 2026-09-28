<script lang="ts">
	import type { Table as TableType, Row } from "@tanstack/table-core";
	import { RestrictToVerticalAxis } from "@dnd-kit/abstract/modifiers";
	import { DragDropProvider } from "@dnd-kit-svelte/svelte";
	import { move } from "@dnd-kit/helpers";
	import { useSortable } from "@dnd-kit-svelte/svelte/sortable";
	import * as Table from "$lib/components/ui/table/index.js";
	import { FlexRender, renderComponent } from "$lib/components/ui/data-table/index.js";
	import type { Schema } from "../schemas.js";

	interface Props {
		table: TableType<Schema>;
		data: Schema[];
		columns: import("@tanstack/table-core").ColumnDef<Schema>[];
	}

	let { table, data = $bindable(), columns }: Props = $props();

	function handleDragEnd(e: CustomEvent) {
		data = move(data, e.detail);
	}
</script>

<div class="overflow-hidden rounded-lg border">
	<DragDropProvider
		modifiers={[
			// @ts-expect-error @dnd-kit/abstract types are botched atm
			RestrictToVerticalAxis,
		]}
		// @ts-expect-error @dnd-kit/abstract types are botched atm
		onDragEnd={handleDragEnd}
	>
		<Table.Root>
			<Table.Header class="bg-muted sticky top-0 z-10">
				{#each table.getHeaderGroups() as headerGroup (headerGroup.id)}
					<Table.Row>
						{#each headerGroup.headers as header (header.id)}
							<Table.Head colspan={header.colSpan}>
								{#if !header.isPlaceholder}
									<FlexRender
										content={header.column.columnDef.header}
										context={header.getContext()}
									/>
								{/if}
							</Table.Head>
						{/each}
					</Table.Row>
				{/each}
			</Table.Header>
			<Table.Body class="**:data-[slot=table-cell]:first:w-8">
				{#if table.getRowModel().rows?.length}
					{#each table.getRowModel().rows as row (row.id)}
						{@render DraggableRow({ row })}
					{/each}
				{:else}
					<Table.Row>
						<Table.Cell colspan={columns.length} class="h-24 text-center">
							No results.
						</Table.Cell>
					</Table.Row>
				{/if}
			</Table.Body>
		</Table.Root>
	</DragDropProvider>
</div>

{#snippet DraggableRow({ row }: { row: Row<Schema> })}
	{@const { ref, isDragging, handleRef } = useSortable({
		id: row.original.id,
		index: () => row.index,
	})}

	<Table.Row
		data-state={row.getIsSelected() && "selected"}
		data-dragging={isDragging.current}
		class="relative z-0 data-[dragging=true]:z-10 data-[dragging=true]:opacity-80"
		{@attach ref}
	>
		{#each row.getVisibleCells() as cell (cell.id)}
			<Table.Cell>
				<FlexRender
					attach={handleRef}
					content={cell.column.columnDef.cell}
					context={cell.getContext()}
				/>
			</Table.Cell>
		{/each}
	</Table.Row>
{/snippet}
