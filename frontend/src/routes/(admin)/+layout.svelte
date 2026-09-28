<script lang="ts">
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';
	import { authStore } from '$lib/stores/auth.svelte';
	import { page } from '$app/state';
	import AppSidebar from '$lib/components/app-sidebar.svelte';
	import SiteHeader from '$lib/components/site-header.svelte';
	import * as Sidebar from '$lib/components/ui/sidebar/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import type { Snippet } from 'svelte';

	let { children }: { children: Snippet } = $props();

	const inLogin = $derived(page.url.pathname === '/login');
	const allowed = $derived(authStore.canAccess(page.url.pathname));

	onMount(async () => {
		await authStore.hydrate();
	});

	const mustChange = $derived(!!authStore.currentUser?.mustChangePassword);
	const inProfile = $derived(page.url.pathname === '/profile');

	$effect(() => {
		if (typeof window === 'undefined' || !authStore.ready) return;
		const loggedIn = authStore.isLoggedIn;
		if (!loggedIn && !inLogin) {
			goto('/login');
		} else if (loggedIn && inLogin) {
			goto(mustChange ? '/profile' : '/dashboard');
		} else if (loggedIn && mustChange && !inProfile && !inLogin) {
			goto('/profile');
		}
	});
</script>

{#if !authStore.ready}
	<div class="bg-muted flex min-h-svh items-center justify-center">
		<p class="text-muted-foreground text-sm">正在恢复登录状态...</p>
	</div>
{:else if inLogin}
	{@render children()}
{:else if authStore.isLoggedIn}
	<Sidebar.Provider
		style="--sidebar-width: calc(var(--spacing) * 64); --header-height: calc(var(--spacing) * 12);"
	>
		<AppSidebar />
		<Sidebar.Inset>
			<SiteHeader />
			<div class="flex flex-1 flex-col">
				<div class="@container/main flex flex-1 flex-col gap-2">
					<div class="flex flex-col gap-4 py-4 md:gap-6 md:py-6">
						{#if mustChange && !inProfile}
							<div class="text-muted-foreground px-4 py-24 text-center text-sm">请先修改初始密码,正在跳转个人中心...</div>
						{:else if allowed}
							{@render children()}
						{:else}
							<div class="flex flex-col items-center justify-center gap-3 px-4 py-24 text-center">
								<h2 class="text-lg font-semibold">没有访问权限</h2>
								<p class="text-muted-foreground text-sm">当前账号无权查看此页面,请联系管理员授权。</p>
								<Button href="/dashboard" variant="outline" size="sm">返回仪表盘</Button>
							</div>
						{/if}
					</div>
				</div>
			</div>
		</Sidebar.Inset>
	</Sidebar.Provider>
{:else}
	<div class="bg-muted flex min-h-svh items-center justify-center">
		<p class="text-muted-foreground text-sm">正在跳转登录...</p>
	</div>
{/if}
