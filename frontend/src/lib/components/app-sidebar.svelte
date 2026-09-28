<script lang="ts">
	import InnerShadowTopIcon from '@tabler/icons-svelte/icons/inner-shadow-top';
	import SettingsIcon from '@tabler/icons-svelte/icons/settings';
	import MonitorIcon from '@tabler/icons-svelte/icons/device-desktop';
	import WrenchIcon from '@tabler/icons-svelte/icons/tool';
	import UserIcon from '@tabler/icons-svelte/icons/user';
	import ShieldIcon from '@tabler/icons-svelte/icons/shield';
	import Menu2Icon from '@tabler/icons-svelte/icons/menu-2';
	import ToolIcon from '@tabler/icons-svelte/icons/tool';
	import LogsIcon from '@tabler/icons-svelte/icons/logs';
	import FileTextIcon from '@tabler/icons-svelte/icons/file-text';
	import AlertTriangleIcon from '@tabler/icons-svelte/icons/alert-triangle';
	import BookIcon from '@tabler/icons-svelte/icons/book';
	import CodeIcon from '@tabler/icons-svelte/icons/code';
	import UsersGroupIcon from '@tabler/icons-svelte/icons/users-group';
	import LayoutDashboardIcon from '@tabler/icons-svelte/icons/layout-dashboard';
	import BuildingIcon from '@tabler/icons-svelte/icons/building';
	import FolderIcon from '@tabler/icons-svelte/icons/folder';
	import BellIcon from '@tabler/icons-svelte/icons/bell';
	import ClipboardListIcon from '@tabler/icons-svelte/icons/clipboard-list';
	import CircleCheckIcon from '@tabler/icons-svelte/icons/circle-check';
	import DevicesIcon from '@tabler/icons-svelte/icons/devices';
	import ActivityIcon from '@tabler/icons-svelte/icons/activity';
	import ClockIcon from '@tabler/icons-svelte/icons/clock';
	import { page } from '$app/state';
	import { authStore } from '$lib/stores/auth.svelte';
	import * as Sidebar from '$lib/components/ui/sidebar/index.js';
	import NavUser from './nav-user.svelte';
	import type { Menu } from '$lib/types/entities';

	interface NavItem {
		title: string;
		url: string;
		icon: typeof UserIcon;
		external?: boolean;
	}

	function isExternalUrl(url: string): boolean {
		return /^https?:\/\//i.test(url);
	}

	interface NavGroup {
		title: string;
		icon: typeof SettingsIcon;
		items: NavItem[];
	}

	const iconMap: Record<string, typeof UserIcon> = {
		Setting: SettingsIcon,
		Settings: SettingsIcon,
		User: UserIcon,
		Role: UsersGroupIcon,
		Menu: Menu2Icon,
		Shield: ShieldIcon,
		Tool: ToolIcon,
		Monitor: MonitorIcon,
		Log: LogsIcon,
		Logs: LogsIcon,
		FileText: FileTextIcon,
		AlertTriangle: AlertTriangleIcon,
		Wrench: WrenchIcon,
		Book: BookIcon,
		Code: CodeIcon,
		Building: BuildingIcon,
		Folder: FolderIcon,
		Bell: BellIcon,
		ClipboardList: ClipboardListIcon,
		CircleCheck: CircleCheckIcon,
		Devices: DevicesIcon,
		Activity: ActivityIcon,
		Clock: ClockIcon
	};

	function iconOf(name?: string): typeof UserIcon {
		if (!name) return SettingsIcon;
		return iconMap[name] ?? SettingsIcon;
	}

	function toGroups(menus: Menu[]): NavGroup[] {
		return menus
			.filter((m) => m.hidden !== 1 && m.type !== 3)
			.map((m) => {
				if (m.type === 1) {
					return {
						title: m.name,
						icon: iconOf(m.icon),
						items: (m.children ?? [])
							.filter((c) => c.type === 2 && c.hidden !== 1 && c.path)
							.map((c) => ({
								title: c.name,
								url: c.path,
								icon: iconOf(c.icon),
								external: isExternalUrl(c.path)
							}))
					};
				}
				return {
					title: m.name,
					icon: iconOf(m.icon),
					items: m.path
						? [{ title: m.name, url: m.path, icon: iconOf(m.icon), external: isExternalUrl(m.path) }]
						: []
				};
			})
			.filter((g) => g.items.length > 0);
	}

	const groups = $derived(toGroups(authStore.menus));
	const currentPath = $derived(page.url.pathname);

	function isItemActive(url: string): boolean {
		if (isExternalUrl(url)) return false;
		return currentPath === url || currentPath.startsWith(url + '/');
	}

	const userData = $derived({
		name: authStore.currentUser?.nickname ?? '管理员',
		email: authStore.currentUser?.email ?? '',
		avatar: authStore.currentUser?.avatar ?? '',
		username: authStore.currentUser?.username ?? ''
	});

	let { ...restProps }: { [key: string]: unknown } = $props();
</script>

<Sidebar.Root collapsible="icon" {...restProps}>
	<Sidebar.Header>
		<Sidebar.Menu>
			<Sidebar.MenuItem>
				<Sidebar.MenuButton class="data-[slot=sidebar-menu-button]:!p-1.5">
					{#snippet child({ props })}
						<a href="/dashboard" {...props}>
							<InnerShadowTopIcon class="!size-5" />
							<span class="text-base font-semibold">S2Admin</span>
						</a>
					{/snippet}
				</Sidebar.MenuButton>
			</Sidebar.MenuItem>
		</Sidebar.Menu>
	</Sidebar.Header>
	<Sidebar.Content>
		<Sidebar.Group>
			<Sidebar.Menu>
				<Sidebar.MenuItem>
					<Sidebar.MenuButton isActive={currentPath === '/dashboard'} tooltipContent="仪表盘">
						{#snippet child({ props })}
							<a href="/dashboard" {...props}>
								<LayoutDashboardIcon />
								<span>仪表盘</span>
							</a>
						{/snippet}
					</Sidebar.MenuButton>
				</Sidebar.MenuItem>
			</Sidebar.Menu>
		</Sidebar.Group>
		{#each groups as group (group.title)}
			<Sidebar.Group>
				<Sidebar.GroupLabel>
					<group.icon class="mr-1 size-4" />
					{group.title}
				</Sidebar.GroupLabel>
				<Sidebar.Menu>
					{#each group.items as item (item.url)}
						<Sidebar.MenuItem>
							<Sidebar.MenuButton isActive={isItemActive(item.url)} tooltipContent={item.title}>
								{#snippet child({ props })}
									<a
										href={item.url}
										target={item.external ? '_blank' : undefined}
										rel={item.external ? 'noopener noreferrer' : undefined}
										{...props}
									>
										<item.icon />
										<span>{item.title}</span>
									</a>
								{/snippet}
							</Sidebar.MenuButton>
						</Sidebar.MenuItem>
					{/each}
				</Sidebar.Menu>
			</Sidebar.Group>
		{/each}
	</Sidebar.Content>
	<Sidebar.Footer>
		<NavUser user={userData} />
	</Sidebar.Footer>
</Sidebar.Root>
