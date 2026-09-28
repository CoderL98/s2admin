<script lang="ts">
	import { page } from '$app/state';
	import { Button } from '$lib/components/ui/button/index.js';

	const status = $derived(page.status);
	const message = $derived(page.error?.message ?? '页面不存在或发生错误');
</script>

<div class="bg-muted flex min-h-svh flex-col items-center justify-center gap-4 p-6 text-center">
	<p class="text-muted-foreground text-sm">{status}</p>
	<h1 class="text-2xl font-semibold">{status === 404 ? '页面未找到' : '出错了'}</h1>
	<p class="text-muted-foreground max-w-md text-sm">{message}</p>
	<Button href="/dashboard" variant="outline" size="sm">返回仪表盘</Button>
</div>
