<script lang="ts">
	import * as Select from '$lib/components/ui/select/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { CHINA_AREA } from '$lib/data/china-area';

	interface Props {
		province?: string;
		city?: string;
		district?: string;
		disabled?: boolean;
	}

	let {
		province = $bindable(''),
		city = $bindable(''),
		district = $bindable(''),
		disabled = false
	}: Props = $props();

	const cities = $derived(CHINA_AREA.find((p) => p.name === province)?.children ?? []);
	const districts = $derived(cities.find((c) => c.name === city)?.children ?? []);

	function onProvince(v: string | undefined) {
		province = v ?? '';
		city = '';
		district = '';
	}

	function onCity(v: string | undefined) {
		city = v ?? '';
		district = '';
	}
</script>

<div class="grid grid-cols-1 gap-2 sm:grid-cols-3">
	<Select.Root type="single" value={province || undefined} onValueChange={onProvince} {disabled}>
		<Select.Trigger>{province || '省/直辖市'}</Select.Trigger>
		<Select.Content class="max-h-72">
			{#each CHINA_AREA as p (p.name)}
				<Select.Item value={p.name}>{p.name}</Select.Item>
			{/each}
		</Select.Content>
	</Select.Root>
	<Select.Root type="single" value={city || undefined} onValueChange={onCity} disabled={disabled || !province}>
		<Select.Trigger>{city || '市/区'}</Select.Trigger>
		<Select.Content class="max-h-72">
			{#each cities as c (c.name)}
				<Select.Item value={c.name}>{c.name}</Select.Item>
			{/each}
		</Select.Content>
	</Select.Root>
	{#if districts.length}
		<Select.Root type="single" value={district || undefined} onValueChange={(v) => (district = v ?? '')} {disabled}>
			<Select.Trigger>{district || '区/县'}</Select.Trigger>
			<Select.Content class="max-h-72">
				{#each districts as d (d.name)}
					<Select.Item value={d.name}>{d.name}</Select.Item>
				{/each}
			</Select.Content>
		</Select.Root>
	{:else}
		<Input bind:value={district} placeholder="区/县(可手填)" disabled={disabled || !city} />
	{/if}
</div>
