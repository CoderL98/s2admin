<script lang="ts">
	import { env } from '$env/dynamic/public';
	import { authorizedFetch } from '$lib/utils/request';

	interface Props {
		src?: string;
		alt?: string;
		class?: string;
	}

	let { src = '', alt = '', class: className = '' }: Props = $props();
	let blobUrl = $state('');

	function resolveUrl(raw: string): string {
		if (!raw) return '';
		if (raw.startsWith('blob:') || raw.startsWith('data:')) return raw;
		let path = raw;
		if (path.startsWith('/uploads/')) {
			path = '/api/system/file/' + path.slice('/uploads/'.length);
		}
		if (path.startsWith('http://') || path.startsWith('https://')) return path;
		const base = env.PUBLIC_API_BASE_URL || 'http://localhost:8080';
		return `${base}${path}`;
	}

	$effect(() => {
		const raw = src;
		blobUrl = '';
		if (!raw) return;
		if (raw.startsWith('blob:') || raw.startsWith('data:')) {
			blobUrl = raw;
			return;
		}
		const url = resolveUrl(raw);
		let cancelled = false;
		let created = '';
		authorizedFetch(url)
			.then((res) => (res.ok ? res.blob() : null))
			.then((blob) => {
				if (cancelled || !blob) return;
				created = URL.createObjectURL(blob);
				blobUrl = created;
			})
			.catch(() => {
				if (!cancelled) blobUrl = '';
			});
		return () => {
			cancelled = true;
			if (created) URL.revokeObjectURL(created);
		};
	});
</script>

{#if blobUrl}
	<img src={blobUrl} {alt} class={className} />
{/if}
