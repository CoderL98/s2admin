<script lang="ts">
	import './layout.css';
	import favicon from '$lib/assets/favicon.svg';
	import { Toaster } from 'svelte-sonner';
	import { localeStore } from '$lib/i18n/locale.svelte';

	let { children } = $props();

	$effect(() => {
		if (typeof document !== 'undefined') document.documentElement.lang = localeStore.locale;
	});
</script>

<svelte:head><link rel="icon" href={favicon} /></svelte:head>
<Toaster position="top-right" richColors />
{@render children()}
