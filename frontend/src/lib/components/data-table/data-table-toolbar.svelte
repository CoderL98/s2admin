<script lang="ts">
	import type { Table } from "@tanstack/table-core";
	import type { Schema } from "../schemas.js";
	import { Button } from "$lib/components/ui/button/index.js";
	import * as DropdownMenu from "$lib/components/ui/dropdown-menu/index.js";
	import * as Select from "$lib/components/ui/select/index.js";
	import { Label } from "$lib/components/ui/label/index.js";
	import * as Tabs from "$lib/components/ui/tabs/index.js";
	import { Badge } from "$lib/components/ui/badge/index.js";
	import LayoutColumnsIcon from "@tabler/icons-svelte/icons/layout-columns";
	import ChevronDownIcon from "@tabler/icons-svelte/icons/chevron-down";
	import PlusIcon from "@tabler/icons-svelte/icons/plus";

	interface Props {
		table: Table<Schema>;
	}

	let { table }: Props = $props();

	let views = [
		{
			id: "outline",
			label: "Outline",
			badge: 0,
		},
		{
			id: "past-performance",
			label: "Past Performance",
			badge: 3,
		},
		{
			id: "key-personnel",
			label: "Key Personnel",
			badge: 2,
		},
		{
			id: "focus-documents",
			label: "Focus Documents",
			badge: 0,
		},
	];

	let view = $state("outline");
	let viewLabel = $derived(views.find((v) => view === v.id)?.label ?? "Select a view");
</script>

<div class="flex items-center justify-between px-4 lg:px-6">
	<Label for="view-selector" class="sr-only">View</Label>
	<Select.Root type="single" bind:value={view}>
		<Select.Trigger class="flex w-fit @4xl/main:hidden" size="sm" id="view-selector">
			{viewLabel}
		</Select.Trigger>
		<Select.Content>
			{#each views as view (view.id)}
				<Select.Item value={view.id}>{view.label}</Select.Item>
			{/each}
		</Select.Content>
	</Select.Root>
	<Tabs.List
		class="**:data-[slot=badge]:bg-muted-foreground/30 hidden **:data-[slot=badge]:size-5 **:data-[slot=badge]:rounded-full **:data-[slot=badge]:px-1 @4xl/main:flex"
	>
		{#each views as view (view.id)}
			<Tabs.Trigger value={view.id}>
				{view.label}
				{#if view.badge > 0}
					<Badge variant="secondary">{view.badge}</Badge>
				{/if}
			</Tabs.Trigger>
		{/each}
	</Tabs.List>
	<div class="flex items-center gap-2">
		<DropdownMenu.Root>
			<DropdownMenu.Trigger>
				{#snippet child({ props })}
					<Button variant="outline" size="sm" {...props}>
						<LayoutColumnsIcon />
						<span class="hidden lg:inline">Customize Columns</span>
						<span class="lg:hidden">Columns</span>
						<ChevronDownIcon />
					</Button>
				{/snippet}
			</DropdownMenu.Trigger>
			<DropdownMenu.Content align="end" class="w-56">
				{#each table
					.getAllColumns()
					.filter((col) => typeof col.accessorFn !== "undefined" && col.getCanHide()) as column (column.id)}
					<DropdownMenu.CheckboxItem
						class="capitalize"
						checked={column.getIsVisible()}
						onCheckedChange={(value: boolean) => column.toggleVisibility(!!value)}
					>
						{column.id}
					</DropdownMenu.CheckboxItem>
				{/each}
			</DropdownMenu.Content>
		</DropdownMenu.Root>
		<Button variant="outline" size="sm">
			<PlusIcon />
			<span class="hidden lg:inline">Add Section</span>
		</Button>
	</div>
</div>
