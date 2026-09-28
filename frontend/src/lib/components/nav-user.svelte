<script lang="ts">
	import LogoutIcon from '@tabler/icons-svelte/icons/logout';
	import UserCircleIcon from '@tabler/icons-svelte/icons/user-circle';
	import DotsVerticalIcon from '@tabler/icons-svelte/icons/dots-vertical';
	import * as Avatar from '$lib/components/ui/avatar/index.js';
	import AuthImage from '$lib/components/auth-image.svelte';
	import * as DropdownMenu from '$lib/components/ui/dropdown-menu/index.js';
	import * as Sidebar from '$lib/components/ui/sidebar/index.js';
	import { authStore } from '$lib/stores/auth.svelte';
	import { goto } from '$app/navigation';

	interface UserProps {
		name: string;
		email: string;
		avatar: string;
		username?: string;
	}

	let { user }: { user: UserProps } = $props();

	const sidebar = Sidebar.useSidebar();

	const initials = $derived(
		(user.username || user.name || 'U').slice(0, 2).toUpperCase()
	);

	async function handleLogout() {
		await authStore.logout();
	}
</script>

<Sidebar.Menu>
	<Sidebar.MenuItem>
		<DropdownMenu.Root>
			<DropdownMenu.Trigger>
				{#snippet child({ props })}
					<Sidebar.MenuButton
						{...props}
						size="lg"
						class="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
					>
						<Avatar.Root class="size-8 rounded-lg">
							{#if user.avatar}
								<AuthImage src={user.avatar} alt={user.name} class="size-8 rounded-lg object-cover" />
							{:else}
								<Avatar.Fallback class="rounded-lg">{initials}</Avatar.Fallback>
							{/if}
						</Avatar.Root>
						<div class="grid flex-1 text-start text-sm leading-tight">
							<span class="truncate font-medium">{user.name}</span>
							<span class="text-muted-foreground truncate text-xs">
								{user.email}
							</span>
						</div>
						<DotsVerticalIcon class="ms-auto size-4" />
					</Sidebar.MenuButton>
				{/snippet}
			</DropdownMenu.Trigger>
			<DropdownMenu.Content
				class="w-(--bits-dropdown-menu-anchor-width) min-w-56 rounded-lg"
				side={sidebar.isMobile ? 'bottom' : 'right'}
				align="end"
				sideOffset={4}
			>
				<DropdownMenu.Label class="p-0 font-normal">
					<div class="flex items-center gap-2 px-1 py-1.5 text-start text-sm">
						<Avatar.Root class="size-8 rounded-lg">
							{#if user.avatar}
								<AuthImage src={user.avatar} alt={user.name} class="size-8 rounded-lg object-cover" />
							{:else}
								<Avatar.Fallback class="rounded-lg">{initials}</Avatar.Fallback>
							{/if}
						</Avatar.Root>
						<div class="grid flex-1 text-start text-sm leading-tight">
							<span class="truncate font-medium">{user.name}</span>
							<span class="text-muted-foreground truncate text-xs">
								{user.email}
							</span>
						</div>
					</div>
				</DropdownMenu.Label>
				<DropdownMenu.Separator />
				<DropdownMenu.Item onclick={() => goto('/profile')}>
					<UserCircleIcon />
					个人中心
				</DropdownMenu.Item>
				<DropdownMenu.Separator />
				<DropdownMenu.Item onclick={handleLogout}>
					<LogoutIcon />
					退出登录
				</DropdownMenu.Item>
			</DropdownMenu.Content>
		</DropdownMenu.Root>
	</Sidebar.MenuItem>
</Sidebar.Menu>