import {
	getCoreRowModel,
	getFacetedRowModel,
	getFacetedUniqueValues,
	getFilteredRowModel,
	getPaginationRowModel,
	getSortedRowModel,
	type ColumnDef,
	type ColumnFiltersState,
	type PaginationState,
	type RowSelectionState,
	type SortingState,
	type VisibilityState,
} from "@tanstack/table-core";

export interface DataTableStateOptions<T> {
	data: T[];
	columns: ColumnDef<T>[];
	enableRowSelection?: boolean;
	autoResetPageIndex?: boolean;
}

export interface DataTableStateReturn<T> {
	data: T[];
	columns: ColumnDef<T>[];
	state: {
		pagination: PaginationState;
		sorting: SortingState;
		columnFilters: ColumnFiltersState;
		rowSelection: RowSelectionState;
		columnVisibility: VisibilityState;
	};
	onPaginationChange: (updater: PaginationState | ((prev: PaginationState) => PaginationState)) => void;
	onSortingChange: (updater: SortingState | ((prev: SortingState) => SortingState)) => void;
	onColumnFiltersChange: (updater: ColumnFiltersState | ((prev: ColumnFiltersState) => ColumnFiltersState)) => void;
	onColumnVisibilityChange: (updater: VisibilityState | ((prev: VisibilityState) => VisibilityState)) => void;
	onRowSelectionChange: (updater: RowSelectionState | ((prev: RowSelectionState) => RowSelectionState)) => void;
	getRowId: (row: T) => string;
	enableRowSelection: boolean;
	autoResetPageIndex: boolean;
	getCoreRowModel: ReturnType<typeof getCoreRowModel>;
	getPaginationRowModel: ReturnType<typeof getPaginationRowModel>;
	getSortedRowModel: ReturnType<typeof getSortedRowModel>;
	getFacetedRowModel: ReturnType<typeof getFacetedRowModel>;
	getFacetedUniqueValues: ReturnType<typeof getFacetedUniqueValues>;
	getFilteredRowModel: ReturnType<typeof getFilteredRowModel>;
}

function updateState<S>(state: S, updater: S | ((prev: S) => S)): S {
	return typeof updater === "function" ? (updater as (prev: S) => S)(state) : updater;
}

export function useDataTableState<T>({
	data,
	columns,
	enableRowSelection = true,
	autoResetPageIndex = false,
}: DataTableStateOptions<T>): DataTableStateReturn<T> {
	let pagination = $state<PaginationState>({ pageIndex: 0, pageSize: 10 });
	let sorting = $state<SortingState>([]);
	let columnFilters = $state<ColumnFiltersState>([]);
	let rowSelection = $state<RowSelectionState>({});
	let columnVisibility = $state<VisibilityState>({});

	return {
		data,
		columns,
		state: {
			get pagination() {
				return pagination;
			},
			get sorting() {
				return sorting;
			},
			get columnFilters() {
				return columnFilters;
			},
			get rowSelection() {
				return rowSelection;
			},
			get columnVisibility() {
				return columnVisibility;
			},
		},
		onPaginationChange: (updater) => {
			pagination = updateState(pagination, updater);
		},
		onSortingChange: (updater) => {
			sorting = updateState(sorting, updater);
		},
		onColumnFiltersChange: (updater) => {
			columnFilters = updateState(columnFilters, updater);
		},
		onColumnVisibilityChange: (updater) => {
			columnVisibility = updateState(columnVisibility, updater);
		},
		onRowSelectionChange: (updater) => {
			rowSelection = updateState(rowSelection, updater);
		},
		getRowId: (row: T) => (row as { id: unknown }).id?.toString() ?? Math.random().toString(),
		enableRowSelection,
		autoResetPageIndex,
		getCoreRowModel: getCoreRowModel(),
		getPaginationRowModel: getPaginationRowModel(),
		getSortedRowModel: getSortedRowModel(),
		getFacetedRowModel: getFacetedRowModel(),
		getFacetedUniqueValues: getFacetedUniqueValues(),
		getFilteredRowModel: getFilteredRowModel(),
	};
}
