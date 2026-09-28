<script lang="ts">
	import { Button } from '$lib/components/ui/button/index.js';
	import { localeStore } from '$lib/i18n/locale.svelte';
</script>

<Button variant="ghost" size="sm" type="button" onclick={() => localeStore.toggle()}>
	{localeStore.t('header.lang')}
</Button>
