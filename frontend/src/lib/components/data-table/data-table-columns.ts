import type { ColumnDef } from "@tanstack/table-core";
import type { Schema } from "../schemas.js";
import { FlexRender, renderComponent } from "$lib/components/ui/data-table/index.js";
import DataTableCheckbox from "../data-table-checkbox.svelte";
import DataTableCellViewer from "../data-table-cell-viewer.svelte";
import DataTableReviewer from "../data-table-reviewer.svelte";
import DataTableActions from "../data-table-actions.svelte";
import DataTableDragHandle from "../data-table-drag-handle.svelte";
import DataTableType from "../data-table-type.svelte";
import DataTableStatus from "../data-table-status.svelte";
import DataTableTarget from "../data-table-target.svelte";
import DataTableLimit from "../data-table-limit.svelte";
import DataTableHeaderTarget from "../data-table-header-target.svelte";
import DataTableHeaderLimit from "../data-table-header-limit.svelte";

export { FlexRender, renderComponent };

export function getDefaultColumns(): ColumnDef<Schema>[] {
	return [
		{
			id: "drag",
			header: () => null,
			cell: () => renderComponent(DataTableDragHandle, {}),
		},
		{
			id: "select",
			header: ({ table }) =>
				renderComponent(DataTableCheckbox, {
					checked: table.getIsAllPageRowsSelected(),
					indeterminate:
						table.getIsSomePageRowsSelected() && !table.getIsAllPageRowsSelected(),
					onCheckedChange: (value: boolean) => table.toggleAllPageRowsSelected(!!value),
					"aria-label": "Select all",
				}),
			cell: ({ row }) =>
				renderComponent(DataTableCheckbox, {
					checked: row.getIsSelected(),
					onCheckedChange: (value: boolean) => row.toggleSelected(!!value),
					"aria-label": "Select row",
				}),
			enableSorting: false,
			enableHiding: false,
		},
		{
			accessorKey: "header",
			header: "Header",
			cell: ({ row }) => renderComponent(DataTableCellViewer, { item: row.original }),
			enableHiding: false,
		},
		{
			accessorKey: "type",
			header: "Section Type",
			cell: ({ row }) => renderComponent(DataTableType, { row }),
		},
		{
			accessorKey: "status",
			header: "Status",
			cell: ({ row }) => renderComponent(DataTableStatus, { row }),
		},
		{
			accessorKey: "target",
			header: () => renderComponent(DataTableHeaderTarget, {}),
			cell: ({ row }) => renderComponent(DataTableTarget, { row }),
		},
		{
			accessorKey: "limit",
			header: () => renderComponent(DataTableHeaderLimit, {}),
			cell: ({ row }) => renderComponent(DataTableLimit, { row }),
		},
		{
			accessorKey: "reviewer",
			header: "Reviewer",
			cell: ({ row }) => renderComponent(DataTableReviewer, { row }),
		},
		{
			id: "actions",
			cell: () => renderComponent(DataTableActions, {}),
		},
	];
}

export const columnPresets = {
	withDrag: true,
	withSelect: true,
	withActions: true,
	visibleColumns: ["header", "type", "status", "target", "limit", "reviewer"],
} as const;

export type ColumnPreset = keyof typeof columnPresets;
