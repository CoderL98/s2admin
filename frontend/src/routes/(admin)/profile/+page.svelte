<script lang="ts">
	import { onMount } from 'svelte';
	import * as Card from '$lib/components/ui/card/index.js';
	import { Field, FieldGroup, FieldLabel } from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { authStore } from '$lib/stores/auth.svelte';
	import {
		updateProfile,
		changePassword,
		uploadAvatar,
		getMySessions,
		kickSession,
		kickOtherSessions,
		getMyLoginLogs,
		getMyOperations
	} from '$lib/api/auth';
	import { USE_MOCK } from '$lib/config';
	import { toast } from 'svelte-sonner';
	import AuthImage from '$lib/components/auth-image.svelte';
	import RegionSelect from '$lib/components/region-select.svelte';
	import { isStrongPassword, PASSWORD_HINT } from '$lib/utils/password';
	import type { LoginSession } from '$lib/types/auth';
	import type { LoginLog, OperationLog } from '$lib/types/entities';

	let nickname = $state('');
	let email = $state('');
	let phone = $state('');
	let avatar = $state('');
	let province = $state('');
	let city = $state('');
	let district = $state('');
	let saving = $state(false);
	let profileError = $state<string | null>(null);

	let oldPassword = $state('');
	let newPassword = $state('');
	let confirmPassword = $state('');
	let pwdSaving = $state(false);
	let pwdError = $state<string | null>(null);

	let sessions = $state<LoginSession[]>([]);
	let myLogs = $state<LoginLog[]>([]);
	let myOps = $state<OperationLog[]>([]);

	onMount(() => {
		syncFromStore();
		void loadExtras();
	});

	function syncFromStore() {
		const u = authStore.currentUser;
		if (!u) return;
		nickname = u.nickname ?? '';
		email = u.email ?? '';
		phone = u.phone ?? '';
		avatar = u.avatar ?? '';
		province = u.province ?? '';
		city = u.city ?? '';
		district = u.district ?? '';
	}

	async function loadExtras() {
		if (authStore.currentUser?.mustChangePassword) return;
		const [s, logs, ops] = await Promise.all([
			getMySessions().catch(() => [] as LoginSession[]),
			getMyLoginLogs({ pageNum: 1, pageSize: 8 }).catch(() => ({ records: [] as LoginLog[] })),
			getMyOperations({ pageNum: 1, pageSize: 8 }).catch(() => ({ records: [] as OperationLog[] }))
		]);
		sessions = s;
		myLogs = logs.records;
		myOps = ops.records;
	}

	async function saveProfile() {
		profileError = null;
		if (!nickname.trim()) {
			profileError = '昵称不能为空';
			return;
		}
		saving = true;
		try {
			authStore.currentUser = await updateProfile({
				nickname: nickname.trim(),
				email,
				phone,
				avatar,
				province,
				city,
				district
			});
			syncFromStore();
			toast.success('资料已保存');
		} catch (e) {
			profileError = e instanceof Error ? e.message : '保存失败';
		} finally {
			saving = false;
		}
	}

	async function onAvatarChange(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		if (!file) return;
		if (USE_MOCK) {
			avatar = URL.createObjectURL(file);
			return;
		}
		try {
			const uploaded = await uploadAvatar(file);
			avatar = uploaded.url;
			toast.success('头像已上传');
		} catch (err) {
			toast.error(err instanceof Error ? err.message : '上传失败');
		} finally {
			input.value = '';
		}
	}

	async function savePassword() {
		pwdError = null;
		if (!isStrongPassword(newPassword)) {
			pwdError = PASSWORD_HINT;
			return;
		}
		if (newPassword !== confirmPassword) {
			pwdError = '两次输入的新密码不一致';
			return;
		}
		pwdSaving = true;
		try {
			await changePassword({ oldPassword, newPassword });
			toast.success('密码已修改,请重新登录');
			oldPassword = '';
			newPassword = '';
			confirmPassword = '';
			await authStore.endSession();
		} catch (e) {
			pwdError = e instanceof Error ? e.message : '修改失败';
		} finally {
			pwdSaving = false;
		}
	}

	async function onKick(sid: string) {
		try {
			await kickSession(sid);
			toast.success('已下线该设备');
			await loadExtras();
		} catch (e) {
			toast.error(e instanceof Error ? e.message : '操作失败');
		}
	}

	async function onKickOthers() {
		try {
			await kickOtherSessions();
			toast.success('已下线其他设备');
			await loadExtras();
		} catch (e) {
			toast.error(e instanceof Error ? e.message : '操作失败');
		}
	}

	function formatTime(iat: number) {
		if (!iat) return '-';
		return new Date(iat).toLocaleString('zh-CN');
	}
</script>

<div class="grid gap-6 px-4 lg:grid-cols-2 lg:px-6">
	{#if authStore.currentUser?.mustChangePassword}
		<div class="bg-amber-50 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200 col-span-full rounded-md px-3 py-2 text-sm">
			当前账号使用初始或重置密码,请先修改密码后再使用系统。
		</div>
	{/if}
	<Card.Root>
		<Card.Header>
			<Card.Title>个人资料</Card.Title>
			<Card.Description>更新昵称、联系方式、地区和头像</Card.Description>
		</Card.Header>
		<Card.Content>
			<FieldGroup>
				{#if profileError}
					<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{profileError}</div>
				{/if}
				<div class="flex items-center gap-4">
					{#if avatar}
						<AuthImage src={avatar} alt="头像" class="size-16 rounded-lg object-cover" />
					{:else}
						<div class="bg-muted text-muted-foreground flex size-16 items-center justify-center rounded-lg text-lg">
							{(nickname || 'U').slice(0, 2).toUpperCase()}
						</div>
					{/if}
					<Field>
						<FieldLabel for="avatar">更换头像</FieldLabel>
						<Input
							id="avatar"
							type="file"
							accept="image/*"
							onchange={onAvatarChange}
							disabled={!!authStore.currentUser?.mustChangePassword}
							aria-label="上传头像"
						/>
					</Field>
				</div>
				<Field>
					<FieldLabel for="nickname">昵称</FieldLabel>
					<Input id="nickname" bind:value={nickname} />
				</Field>
				<Field>
					<FieldLabel for="email">邮箱</FieldLabel>
					<Input id="email" type="email" bind:value={email} />
				</Field>
				<Field>
					<FieldLabel for="phone">手机号</FieldLabel>
					<Input id="phone" bind:value={phone} />
				</Field>
				<Field>
					<FieldLabel>所在地区</FieldLabel>
					<RegionSelect bind:province bind:city bind:district disabled={!!authStore.currentUser?.mustChangePassword} />
				</Field>
				<Button onclick={saveProfile} disabled={saving || !!authStore.currentUser?.mustChangePassword}>
					{saving ? '保存中...' : '保存资料'}
				</Button>
			</FieldGroup>
		</Card.Content>
	</Card.Root>

	<Card.Root>
		<Card.Header>
			<Card.Title>修改密码</Card.Title>
			<Card.Description>验证原密码后设置新密码,成功后需要重新登录</Card.Description>
		</Card.Header>
		<Card.Content>
			<FieldGroup>
				{#if pwdError}
					<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{pwdError}</div>
				{/if}
				<Field>
					<FieldLabel for="oldPassword">原密码</FieldLabel>
					<Input id="oldPassword" type="password" bind:value={oldPassword} />
				</Field>
				<Field>
					<FieldLabel for="newPassword">新密码</FieldLabel>
					<Input id="newPassword" type="password" bind:value={newPassword} />
					<p class="text-muted-foreground text-xs">{PASSWORD_HINT}</p>
				</Field>
				<Field>
					<FieldLabel for="confirmPassword">确认新密码</FieldLabel>
					<Input id="confirmPassword" type="password" bind:value={confirmPassword} />
				</Field>
				<Button onclick={savePassword} disabled={pwdSaving}>
					{pwdSaving ? '提交中...' : '修改密码'}
				</Button>
			</FieldGroup>
		</Card.Content>
	</Card.Root>

	<Card.Root class="col-span-full">
		<Card.Header class="flex flex-row items-center justify-between gap-2">
			<div>
				<Card.Title>登录设备</Card.Title>
				<Card.Description>当前账号的在线会话,可下线其他设备</Card.Description>
			</div>
			<Button variant="outline" size="sm" onclick={onKickOthers} disabled={!!authStore.currentUser?.mustChangePassword}>
				下线其他设备
			</Button>
		</Card.Header>
		<Card.Content>
			{#if sessions.length === 0}
				<p class="text-muted-foreground text-sm">暂无会话记录。将系统参数 sys.account.maxSessions 设为 1 或 3 可限制同时在线端数。</p>
			{:else}
				<div class="divide-y">
					{#each sessions as s (s.sid)}
						<div class="flex flex-wrap items-center justify-between gap-2 py-3 text-sm">
							<div>
								<p class="font-medium">{s.ip || '未知 IP'}{#if s.current}<span class="text-primary ml-2 text-xs">当前设备</span>{/if}</p>
								<p class="text-muted-foreground max-w-xl truncate text-xs">{s.ua || '未知客户端'}</p>
							</div>
							<div class="flex items-center gap-3">
								<span class="text-muted-foreground text-xs">{formatTime(s.iat)}</span>
								{#if !s.current}
									<Button variant="outline" size="sm" onclick={() => onKick(s.sid)}>下线</Button>
								{/if}
							</div>
						</div>
					{/each}
				</div>
			{/if}
		</Card.Content>
	</Card.Root>

	<Card.Root>
		<Card.Header>
			<Card.Title>我的登录记录</Card.Title>
			<Card.Description>最近 8 条本人登录</Card.Description>
		</Card.Header>
		<Card.Content>
			{#if myLogs.length === 0}
				<p class="text-muted-foreground text-sm">暂无登录记录</p>
			{:else}
				<div class="divide-y">
					{#each myLogs as log (log.id)}
						<div class="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
							<div>
								<p class="font-medium">{log.status === 0 ? '成功' : '失败'}</p>
								<p class="text-muted-foreground text-xs">{log.ip} · {log.browser} · {log.os}</p>
							</div>
							<span class="text-muted-foreground text-xs">{log.loginTime}</span>
						</div>
					{/each}
				</div>
			{/if}
		</Card.Content>
	</Card.Root>

	<Card.Root>
		<Card.Header>
			<Card.Title>我的操作记录</Card.Title>
			<Card.Description>最近 8 条本人操作</Card.Description>
		</Card.Header>
		<Card.Content>
			{#if myOps.length === 0}
				<p class="text-muted-foreground text-sm">暂无操作记录</p>
			{:else}
				<div class="divide-y">
					{#each myOps as op (op.id)}
						<div class="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
							<div>
								<p class="font-medium">{op.module} · {op.operation}</p>
								<p class="text-muted-foreground text-xs">{op.method} {op.url}</p>
							</div>
							<span class="text-muted-foreground text-xs">{op.operationTime}</span>
						</div>
					{/each}
				</div>
			{/if}
		</Card.Content>
	</Card.Root>
</div>
