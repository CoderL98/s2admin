<script lang="ts" generics="T">
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { cn } from '$lib/utils';
	import type { Snippet } from 'svelte';

	interface Props {
		open: boolean;
		title: string;
		description?: string;
		submitText?: string;
		cancelText?: string;
		loading?: boolean;
		submitDisabled?: boolean;
		/** 当 open 变化时触发(从 false→true 表示打开,可重置表单) */
		onOpenChange?: (open: boolean) => void;
		/** 点击确认;resolve void 即关闭弹窗,throw 即不关闭 */
		onSubmit?: () => Promise<void> | void;
		contentClass?: string;
		children: Snippet;
	}

	let {
		open = $bindable(false),
		title,
		description,
		submitText = '确定',
		cancelText = '取消',
		loading = false,
		submitDisabled = false,
		onOpenChange,
		onSubmit,
		contentClass,
		children
	}: Props = $props();

	async function handleSubmit() {
		if (!onSubmit) {
			open = false;
			return;
		}
		try {
			await onSubmit();
			open = false;
		} catch (e) {
			// 不关闭弹窗,让表单内自处理错误
			console.error(e);
		}
	}
</script>

<Dialog.Root bind:open onOpenChange={onOpenChange}>
	<Dialog.Content class={cn('sm:max-w-[520px]', contentClass)}>
		<Dialog.Header>
			<Dialog.Title>{title}</Dialog.Title>
			{#if description}
				<Dialog.Description>{description}</Dialog.Description>
			{/if}
		</Dialog.Header>
		<div class="grid gap-4 py-2">
			{@render children()}
		</div>
		<Dialog.Footer>
			<Button variant="outline" type="button" onclick={() => (open = false)} disabled={loading}>
				{cancelText}
			</Button>
			<Button type="button" onclick={handleSubmit} disabled={loading || submitDisabled}>
				{loading ? '处理中...' : submitText}
			</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>