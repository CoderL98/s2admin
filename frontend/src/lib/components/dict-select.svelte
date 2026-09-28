<script lang="ts">
	import { onMount } from 'svelte';
	import * as Select from '$lib/components/ui/select/index.js';
	import { getDictDataByTypeCode } from '$lib/api/dict';
	import type { DictData } from '$lib/types/entities';

	interface Props {
		typeCode: string;
		value?: string;
		onValueChange?: (value: string) => void;
		placeholder?: string;
		allowEmpty?: boolean;
		emptyLabel?: string;
		disabled?: boolean;
		id?: string;
		class?: string;
	}

	let {
		typeCode,
		value = $bindable(''),
		onValueChange,
		placeholder = '请选择',
		allowEmpty = false,
		emptyLabel = '全部',
		disabled = false,
		id,
		class: className = ''
	}: Props = $props();

	let options = $state<DictData[]>([]);

	const display = $derived(
		value === '' || value == null
			? allowEmpty
				? emptyLabel
				: placeholder
			: (options.find((o) => o.value === String(value))?.label ?? placeholder)
	);

	const FALLBACK: Record<string, DictData[]> = {
		user_status: [
			{ id: 1, dictTypeId: 0, label: '正常', value: '0', sort: 0, status: 0 },
			{ id: 2, dictTypeId: 0, label: '禁用', value: '1', sort: 1, status: 0 },
			{ id: 3, dictTypeId: 0, label: '锁定', value: '2', sort: 2, status: 0 },
			{ id: 4, dictTypeId: 0, label: '过期', value: '3', sort: 3, status: 0 }
		],
		login_status: [
			{ id: 1, dictTypeId: 0, label: '成功', value: '0', sort: 0, status: 0 },
			{ id: 2, dictTypeId: 0, label: '失败', value: '1', sort: 1, status: 0 }
		]
	};

	onMount(async () => {
		try {
			options = (await getDictDataByTypeCode(typeCode))
				.filter((d) => d.status === 0)
				.sort((a, b) => a.sort - b.sort);
		} catch {
			options = FALLBACK[typeCode] ?? [];
		}
	});

	const ALL = '__all__';

	function change(v: string | undefined) {
		const next = !v || v === ALL ? '' : v;
		value = next;
		onValueChange?.(next);
	}
</script>

<Select.Root type="single" value={value === '' || value == null ? (allowEmpty ? ALL : undefined) : String(value)} onValueChange={change} {disabled}>
	<Select.Trigger {id} class={className}>{display}</Select.Trigger>
	<Select.Content>
		{#if allowEmpty}
			<Select.Item value={ALL}>{emptyLabel}</Select.Item>
		{/if}
		{#each options as opt (opt.id)}
			<Select.Item value={opt.value}>{opt.label}</Select.Item>
		{/each}
	</Select.Content>
</Select.Root>
