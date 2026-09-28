<script lang="ts">
	import * as Card from '$lib/components/ui/card/index.js';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { generateCode, type CodegenField } from '$lib/api/codegen';
	import { authStore } from '$lib/stores/auth.svelte';
	import { toast } from 'svelte-sonner';

	let moduleName = $state('system');
	let entity = $state('Product');
	let tableName = $state('sys_product');
	let permission = $state('system:product');
	let path = $state('/system/product');
	let fields = $state<CodegenField[]>([
		{ name: 'name', javaType: 'String', label: '名称', required: true, query: true },
		{ name: 'status', javaType: 'Integer', label: '状态', required: false, query: true }
	]);
	let generating = $state(false);

	const canGenerate = $derived(authStore.hasPermission('tools:codegen:generate'));

	function addField() {
		fields = [...fields, { name: '', javaType: 'String', label: '', required: false, query: false }];
	}

	function removeField(i: number) {
		fields = fields.filter((_, idx) => idx !== i);
	}

	async function generate() {
		if (!entity.trim() || fields.some((f) => !f.name || !f.label)) {
			toast.error('请完整填写实体名和字段');
			return;
		}
		generating = true;
		try {
			await generateCode({
				module: moduleName,
				entity,
				tableName,
				permission,
				path,
				fields
			});
			toast.success('已下载代码包');
		} catch (e) {
			toast.error(e instanceof Error ? e.message : '生成失败');
		} finally {
			generating = false;
		}
	}
</script>

<div class="px-4 lg:px-6">
	<Card.Root class="mx-auto max-w-3xl">
		<Card.Header>
			<Card.Title>代码生成</Card.Title>
			<Card.Description>按字段生成 entity / form / service / controller 与前端列表页骨架,下载 zip。</Card.Description>
		</Card.Header>
		<Card.Content>
			<FieldGroup>
				<div class="grid grid-cols-2 gap-3">
					<Field>
						<FieldLabel>模块</FieldLabel>
						<Input bind:value={moduleName} />
					</Field>
					<Field>
						<FieldLabel>实体名</FieldLabel>
						<Input bind:value={entity} />
					</Field>
					<Field>
						<FieldLabel>表名</FieldLabel>
						<Input bind:value={tableName} />
					</Field>
					<Field>
						<FieldLabel>权限前缀</FieldLabel>
						<Input bind:value={permission} />
					</Field>
					<Field>
						<FieldLabel>前端路径</FieldLabel>
						<Input bind:value={path} />
					</Field>
				</div>
				<div class="flex items-center justify-between">
					<p class="text-sm font-medium">字段</p>
					{#if canGenerate}
						<Button variant="outline" size="sm" onclick={addField}>加字段</Button>
					{/if}
				</div>
				<div class="grid gap-2">
					{#each fields as field, i (i)}
						<div class="grid grid-cols-6 items-center gap-2">
							<Input class="col-span-2" placeholder="name" bind:value={field.name} />
							<Input placeholder="String" bind:value={field.javaType} />
							<Input placeholder="标签" bind:value={field.label} />
							<label class="text-muted-foreground text-xs">
								<input type="checkbox" bind:checked={field.query} /> 查询
							</label>
							{#if canGenerate}
								<Button variant="ghost" size="sm" onclick={() => removeField(i)}>删</Button>
							{/if}
						</div>
					{/each}
				</div>
				{#if canGenerate}
					<Button onclick={generate} disabled={generating}>{generating ? '生成中...' : '生成并下载'}</Button>
				{:else}
					<p class="text-muted-foreground text-sm">当前账号无代码生成权限</p>
				{/if}
			</FieldGroup>
		</Card.Content>
	</Card.Root>
</div>
