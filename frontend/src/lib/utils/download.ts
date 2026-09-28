import { env } from '$env/dynamic/public';
import { authorizedFetch } from '$lib/utils/request';

const API_BASE_URL = env.PUBLIC_API_BASE_URL || 'http://localhost:8080';

export async function downloadAuthenticated(endpoint: string, filename: string) {
	const url = endpoint.startsWith('http') ? endpoint : `${API_BASE_URL}${endpoint}`;
	const res = await authorizedFetch(url);
	const contentType = res.headers.get('content-type') ?? '';
	if (!res.ok || contentType.includes('application/json')) {
		let message = '下载失败';
		try {
			const body = (await res.json()) as { message?: string };
			if (body?.message) message = body.message;
		} catch {
			/* ignore */
		}
		throw new Error(message);
	}
	const blob = await res.blob();
	const objectUrl = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = objectUrl;
	a.download = filename;
	a.click();
	URL.revokeObjectURL(objectUrl);
}

export function withQuery(endpoint: string, params: Record<string, string | number | undefined>): string {
	const search = new URLSearchParams();
	for (const [key, value] of Object.entries(params)) {
		if (value === undefined || value === null || value === '') continue;
		search.append(key, String(value));
	}
	const qs = search.toString();
	return qs ? `${endpoint}?${qs}` : endpoint;
}
