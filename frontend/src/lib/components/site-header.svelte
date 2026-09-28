<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Separator } from '$lib/components/ui/separator/index.js';
	import * as Sidebar from '$lib/components/ui/sidebar/index.js';
	import * as DropdownMenu from '$lib/components/ui/dropdown-menu/index.js';
	import { authStore } from '$lib/stores/auth.svelte';
	import { getUnreadCount } from '$lib/api/message';
	import { getPublishedNotices } from '$lib/api/notice';
	import { getTenantOptions } from '$lib/api/tenant';
	import { getTenantScope, setTenantScope } from '$lib/tenant-scope';
	import { localeStore } from '$lib/i18n/locale.svelte';
	import LocaleSwitch from '$lib/components/locale-switch.svelte';
	import type { Notice, Tenant } from '$lib/types/entities';
	import BellIcon from '@tabler/icons-svelte/icons/bell';
	import MailIcon from '@tabler/icons-svelte/icons/mail';

	const fallbackTitles: Record<string, string> = {
		'/dashboard': '仪表盘',
		'/system/user': '用户管理',
		'/system/role': '角色管理',
		'/system/menu': '菜单管理',
		'/system/permission': '权限管理',
		'/system/config': '系统配置',
		'/system/dept': '部门管理',
		'/system/file': '文件管理',
		'/system/notice': '公告管理',
		'/system/flow': '流程定义',
		'/system/approval': '审批中心',
		'/system/tenant': '租户管理',
		'/monitor/online': '在线用户',
		'/monitor/server': '服务监控',
		'/monitor/job': '定时任务',
		'/monitor/login-log': '登录日志',
		'/monitor/op-log': '操作日志',
		'/monitor/error-log': '异常日志',
		'/tools/dict': '字典管理',
		'/tools/build': '代码生成',
		'/profile': '个人中心',
		'/message': '站内信'
	};

	function findMenuName(nodes: { name: string; path?: string; children?: unknown[] }[], path: string): string | null {
		for (const node of nodes) {
			const child = node.children as typeof nodes | undefined;
			if (child?.length) {
				const found = findMenuName(child, path);
				if (found) return found;
			}
			if (node.path && (path === node.path || path.startsWith(node.path + '/'))) return node.name;
		}
		return null;
	}

	const title = $derived.by(() => {
		const p = page.url.pathname;
		if (localeStore.locale !== 'zh-CN') {
			const translated = localeStore.t('route:' + p);
			if (translated !== 'route:' + p) return translated;
		}
		return findMenuName(authStore.menus, p) ?? fallbackTitles[p] ?? 'S2Admin';
	});

	let unread = $state(0);
	let notices = $state<Notice[]>([]);
	let tenants = $state<Tenant[]>([]);
	let tenantScope = $state('');
	const platform = $derived(authStore.currentUser?.roles?.includes('SUPER_ADMIN') ?? false);

	onMount(async () => {
		tenantScope = getTenantScope() ?? '';
		if (authStore.currentUser?.mustChangePassword) return;
		unread = await getUnreadCount().catch(() => 0);
		notices = (await getPublishedNotices().catch(() => [])).slice(0, 5);
	});

	$effect(() => {
		if (!platform) return;
		getTenantOptions()
			.then((list) => (tenants = list))
			.catch(() => (tenants = []));
	});

	function onTenant(event: Event) {
		const value = (event.currentTarget as HTMLSelectElement).value;
		setTenantScope(value || null);
		window.location.reload();
	}
</script>

<header
	class="flex h-(--header-height) shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)"
>
	<div class="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
		<Sidebar.Trigger class="-ms-1" />
		<Separator orientation="vertical" class="mx-2 data-[orientation=vertical]:h-4" />
		<h1 class="text-base font-medium">{title}</h1>
		<div class="ms-auto flex items-center gap-1">
			{#if platform}
				<select
					class="border-input bg-background h-8 max-w-40 rounded-md border px-2 text-sm"
					value={tenantScope}
					onchange={onTenant}
				>
					<option value="">{localeStore.t('tenant.all')}</option>
					{#each tenants as tenant (tenant.id)}
						<option value={String(tenant.id)}>{tenant.name}</option>
					{/each}
				</select>
			{/if}
			<LocaleSwitch />
			<DropdownMenu.Root>
				<DropdownMenu.Trigger>
					{#snippet child({ props })}
						<Button variant="ghost" size="icon" class="relative" {...props}>
							<BellIcon class="size-4" />
							{#if notices.length}
								<span class="bg-primary absolute top-1 right-1 size-1.5 rounded-full"></span>
							{/if}
						</Button>
					{/snippet}
				</DropdownMenu.Trigger>
				<DropdownMenu.Content align="end" class="w-72">
					<DropdownMenu.Label>{localeStore.t('header.notices')}</DropdownMenu.Label>
					{#if notices.length === 0}
						<DropdownMenu.Item disabled>{localeStore.t('header.noNotice')}</DropdownMenu.Item>
					{:else}
						{#each notices as n (n.id)}
							<DropdownMenu.Item class="flex flex-col items-start gap-0.5">
								<span class="text-sm">{n.title}</span>
								<span class="text-muted-foreground text-xs">{n.publishTime ?? ''}</span>
							</DropdownMenu.Item>
						{/each}
					{/if}
				</DropdownMenu.Content>
			</DropdownMenu.Root>
			<Button variant="ghost" size="icon" class="relative" href="/message">
				<MailIcon class="size-4" />
				{#if unread > 0}
					<span
						class="bg-destructive text-destructive-foreground absolute -top-0.5 -right-0.5 min-w-4 rounded-full px-1 text-[10px] leading-4"
					>
						{unread > 99 ? '99+' : unread}
					</span>
				{/if}
			</Button>
			<Button variant="ghost" size="sm" class="text-muted-foreground">
				{authStore.currentUser?.nickname ?? localeStore.t('header.guest')}
			</Button>
		</div>
	</div>
</header>
