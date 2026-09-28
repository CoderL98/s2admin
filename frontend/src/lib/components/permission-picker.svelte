<script lang="ts">
	import { Checkbox } from '$lib/components/ui/checkbox/index.js';
	import type { Permission } from '$lib/types/entities';
	import { PERMISSION_TYPE_OPTIONS } from '$lib/types/entities';

	interface Props {
		items: Permission[];
		selectedIds: number[];
	}

	let { items, selectedIds = $bindable() }: Props = $props();

	const groups = $derived.by(() => {
		const roots = items.filter((p) => !p.parentId);
		const used = new Set(roots.map((r) => r.id));
		const grouped = roots.map((root) => ({
			root,
			children: items.filter((c) => c.parentId === root.id)
		}));
		const orphans = items.filter((p) => p.parentId && !used.has(p.parentId) && !roots.some((r) => r.id === p.id));
		if (orphans.length) {
			grouped.push({
				root: {
					id: 0,
					name: '其他',
					code: '',
					type: 1,
					parentId: 0,
					sort: 999,
					status: 0
				},
				children: orphans
			});
		}
		return grouped;
	});

	function typeLabel(type: 1 | 2 | 3) {
		return PERMISSION_TYPE_OPTIONS.find((o) => o.value === type)?.label ?? '';
	}

	function isChecked(id: number) {
		return selectedIds.includes(id);
	}

	function toggle(id: number, checked: boolean) {
		if (checked) {
			if (!selectedIds.includes(id)) selectedIds = [...selectedIds, id];
		} else {
			selectedIds = selectedIds.filter((x) => x !== id);
		}
	}

	function toggleGroup(group: (typeof groups)[number], checked: boolean) {
		const ids = [group.root.id, ...group.children.map((c) => c.id)].filter((id) => id > 0);
		if (checked) {
			selectedIds = [...new Set([...selectedIds, ...ids])];
		} else {
			selectedIds = selectedIds.filter((id) => !ids.includes(id));
		}
	}
</script>

<div class="max-h-72 space-y-3 overflow-auto rounded-md border p-3">
	{#each groups as group (group.root.id)}
		<div class="space-y-2">
			<label class="flex items-center gap-2 text-sm font-medium">
				<Checkbox
					checked={group.root.id > 0 ? isChecked(group.root.id) : group.children.every((c) => isChecked(c.id))}
					onCheckedChange={(v: boolean | 'indeterminate') => toggleGroup(group, v === true)}
				/>
				<span>{group.root.name}</span>
				{#if group.root.code}
					<code class="text-muted-foreground text-xs">{group.root.code}</code>
				{/if}
			</label>
			{#if group.children.length}
				<div class="ml-6 grid gap-2 sm:grid-cols-2">
					{#each group.children as child (child.id)}
						<label class="flex items-center gap-2 text-sm">
							<Checkbox
								checked={isChecked(child.id)}
								onCheckedChange={(v: boolean | 'indeterminate') => toggle(child.id, v === true)}
							/>
							<span>{child.name}</span>
							<span class="text-muted-foreground text-xs">{typeLabel(child.type)}</span>
						</label>
					{/each}
				</div>
			{/if}
		</div>
	{/each}
	{#if items.length === 0}
		<p class="text-muted-foreground text-sm">暂无权限项</p>
	{/if}
</div>
