<script lang="ts">
	import { goto } from '$app/navigation';
	import { authStore } from '$lib/stores/auth.svelte';
	import { onMount } from 'svelte';

	onMount(() => {
		goto(authStore.isLoggedIn ? '/dashboard' : '/login');
	});
</script>

<div class="bg-muted flex min-h-svh items-center justify-center">
	<p class="text-muted-foreground text-sm">跳转中...</p>
</div>