<script lang="ts">
	import {
		FieldGroup,
		Field,
		FieldLabel,
		FieldDescription,
		FieldSeparator
	} from '$lib/components/ui/field/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';
	import { authStore } from '$lib/stores/auth.svelte';
	import { getCaptcha, forgotPassword, resetPassword, oauthProviders } from '$lib/api/auth';
	import { localeStore } from '$lib/i18n/locale.svelte';
	import LocaleSwitch from '$lib/components/locale-switch.svelte';
	import { env } from '$env/dynamic/public';
	import { Checkbox } from '$lib/components/ui/checkbox/index.js';
	import { isStrongPassword, PASSWORD_HINT } from '$lib/utils/password';
	import GalleryVerticalEndIcon from '@lucide/svelte/icons/gallery-vertical-end';
	import { APP_NAME } from '$lib/config';
	import type { CaptchaResult } from '$lib/types/auth';

	let username = $state('admin');
	let password = $state('admin123');
	let captcha = $state('');
	let captchaInfo = $state<CaptchaResult | null>(null);
	let rememberMe = $state(false);
	let error = $state<string | null>(null);
	let mode = $state<'login' | 'forgot'>('login');
	let resetAccount = $state('');
	let resetCode = $state('');
	let resetPwd = $state('');
	let mockHint = $state('');
	let providers = $state<{ id: string; name: string }[]>([]);
	const apiBase = env.PUBLIC_API_BASE_URL || 'http://localhost:8080';

	async function loadCaptcha() {
		try {
			captchaInfo = await getCaptcha();
			captcha = '';
		} catch {
			captchaInfo = { enabled: false };
		}
	}

	onMount(async () => {
		const params = new URLSearchParams(window.location.search);
		const oauthToken = params.get('oauthToken');
		const oauthRefresh = params.get('oauthRefresh');
		const oauthError = params.get('oauthError');
		if (oauthToken || oauthRefresh || oauthError) {
			history.replaceState({}, '', window.location.pathname);
		}
		if (oauthError) error = oauthError;
		if (oauthToken && oauthRefresh) {
			try {
				await authStore.acceptTokens(oauthToken, oauthRefresh);
				await goto(authStore.currentUser?.mustChangePassword ? '/profile' : '/dashboard');
				return;
			} catch (err) {
				error = err instanceof Error ? err.message : localeStore.t('login.failed');
			}
		}
		await loadCaptcha();
		providers = await oauthProviders().catch(() => []);
	});

	async function handleSubmit(e: SubmitEvent) {
		e.preventDefault();
		error = null;
		try {
			await authStore.login(username, password, captcha || undefined, captchaInfo?.captchaKey, rememberMe);
			await goto(authStore.currentUser?.mustChangePassword ? '/profile' : '/dashboard');
		} catch (err) {
			error = err instanceof Error ? err.message : localeStore.t('login.failed');
			await loadCaptcha();
		}
	}

	async function sendCode() {
		error = null;
		mockHint = '';
		try {
			const r = await forgotPassword(
				resetAccount || username,
				captcha || undefined,
				captchaInfo?.captchaKey
			);
			mockHint = r.mockCode ? `开发环境验证码: ${r.mockCode}` : r.message;
		} catch (err) {
			error = err instanceof Error ? err.message : localeStore.t('login.sendFailed');
		} finally {
			await loadCaptcha();
		}
	}

	async function doReset() {
		error = null;
		if (!isStrongPassword(resetPwd)) {
			error = PASSWORD_HINT;
			return;
		}
		try {
			await resetPassword(resetAccount || username, resetCode, resetPwd);
			mode = 'login';
			password = '';
			error = null;
			mockHint = '密码已重置,请登录';
		} catch (err) {
			error = err instanceof Error ? err.message : localeStore.t('login.resetFailed');
		}
	}
</script>

<div class="bg-muted flex min-h-svh flex-col items-center justify-center gap-6 p-6 md:p-10">
	<div class="flex w-full max-w-sm flex-col gap-6">
		<div class="flex items-center justify-between">
			<a href="/login" class="flex items-center gap-2 font-medium">
			<div class="bg-primary text-primary-foreground flex size-6 items-center justify-center rounded-md">
				<GalleryVerticalEndIcon class="size-4" />
			</div>
			{APP_NAME}
			</a>
			<LocaleSwitch />
		</div>
		{#if mode === 'login'}
		<form class="bg-card flex flex-col gap-6 rounded-xl border p-6 shadow-sm" onsubmit={handleSubmit}>
			<FieldGroup>
				<div class="flex flex-col items-center gap-1 text-center">
					<h1 class="text-2xl font-bold">{localeStore.t('login.title')} {APP_NAME}</h1>
					<p class="text-muted-foreground text-sm text-balance">{localeStore.t('login.subtitle')}</p>
				</div>

				{#if error}
					<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">
						{error}
					</div>
				{/if}

				<Field>
					<FieldLabel for="username">{localeStore.t('login.account')}</FieldLabel>
					<Input
						id="username"
						type="text"
						placeholder={localeStore.t('login.accountPh')}
						required
						autocomplete="username"
						bind:value={username}
					/>
				</Field>
				<Field>
					<FieldLabel for="password">{localeStore.t('login.password')}</FieldLabel>
					<Input
						id="password"
						type="password"
						required
						autocomplete="current-password"
						bind:value={password}
					/>
				</Field>
				<label class="flex items-center gap-2 text-sm">
					<Checkbox bind:checked={rememberMe} />
					<span>{localeStore.t('login.remember')}</span>
				</label>
				{#if captchaInfo?.enabled}
					<Field>
						<FieldLabel for="captcha">{localeStore.t('login.captcha')}</FieldLabel>
						<div class="flex items-center gap-2">
							<Input
								id="captcha"
								bind:value={captcha}
								required
								autocomplete="off"
								placeholder={localeStore.t('login.captchaPh')}
								class="flex-1"
							/>
							<button
								type="button"
								class="border-input h-9 w-[120px] shrink-0 overflow-hidden rounded-md border"
								onclick={loadCaptcha}
								title="点击刷新验证码"
							>
								{#if captchaInfo.image}
									<img src={captchaInfo.image} alt="验证码" class="h-full w-full object-cover" />
								{/if}
							</button>
						</div>
					</Field>
				{/if}
				<Field>
					<Button type="submit" disabled={authStore.loading}>
						{authStore.loading ? localeStore.t('login.submitting') : localeStore.t('login.submit')}
					</Button>
				</Field>
				<button type="button" class="text-muted-foreground text-sm underline" onclick={() => (mode = 'forgot')}>
					{localeStore.t('login.forgot')}
				</button>
				{#if providers.length}
					<FieldSeparator>{localeStore.t('login.oauth')}</FieldSeparator>
					<div class="grid grid-cols-2 gap-2">
						{#each providers as provider (provider.id)}
							<Button
								type="button"
								variant="outline"
								href={`${apiBase}/api/auth/oauth/${provider.id}/authorize`}
							>
								{localeStore.t('oauth.' + provider.id)}
							</Button>
						{/each}
					</div>
				{:else}
					<p class="text-muted-foreground text-center text-xs">{localeStore.t('login.oauthEmpty')}</p>
				{/if}
				<FieldSeparator>{localeStore.t('login.hint')}</FieldSeparator>
				<FieldDescription class="text-center">
					默认账号 <code class="bg-muted rounded px-1 py-0.5">admin</code>
					/ 密码 <code class="bg-muted rounded px-1 py-0.5">admin123</code>
				</FieldDescription>
			</FieldGroup>
		</form>
		{:else}
		<div class="bg-card flex flex-col gap-4 rounded-xl border p-6 shadow-sm">
			<h1 class="text-center text-2xl font-bold">{localeStore.t('login.reset')}</h1>
			{#if error}
				<div class="bg-destructive/10 text-destructive rounded-md px-3 py-2 text-sm">{error}</div>
			{/if}
			{#if mockHint}
				<div class="bg-muted rounded-md px-3 py-2 text-sm">{mockHint}</div>
			{/if}
			<Field>
				<FieldLabel>{localeStore.t('login.resetAccount')}</FieldLabel>
				<Input bind:value={resetAccount} placeholder={username} />
			</Field>
			{#if captchaInfo?.enabled}
				<div class="flex items-center gap-2">
					<Input bind:value={captcha} placeholder={localeStore.t('login.imageCaptcha')} class="flex-1" />
					<button type="button" class="border-input h-9 w-[120px] shrink-0 overflow-hidden rounded-md border" onclick={loadCaptcha}>
						{#if captchaInfo.image}
							<img src={captchaInfo.image} alt="验证码" class="h-full w-full object-cover" />
						{/if}
					</button>
				</div>
			{/if}
			<div class="flex gap-2">
				<Input class="flex-1" bind:value={resetCode} placeholder={localeStore.t('login.mailCode')} />
				<Button type="button" variant="outline" onclick={sendCode}>{localeStore.t('login.sendCode')}</Button>
			</div>
			<Field>
				<FieldLabel>{localeStore.t('login.newPassword')}</FieldLabel>
				<Input type="password" bind:value={resetPwd} />
				<p class="text-muted-foreground text-xs">{PASSWORD_HINT}</p>
			</Field>
			<Button onclick={doReset}>{localeStore.t('login.confirmReset')}</Button>
			<button type="button" class="text-muted-foreground text-sm underline" onclick={() => (mode = 'login')}>
				{localeStore.t('login.back')}
			</button>
		</div>
		{/if}
	</div>
</div>
