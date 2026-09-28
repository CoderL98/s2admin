import { authorizedFetch } from '$lib/utils/request';
import { env } from '$env/dynamic/public';

const API_BASE_URL = env.PUBLIC_API_BASE_URL || 'http://localhost:8080';

export interface CodegenField {
	name: string;
	javaType: string;
	label: string;
	query?: boolean;
	required?: boolean;
}

export interface CodegenPayload {
	module: string;
	entity: string;
	tableName: string;
	permission: string;
	path: string;
	remark?: string;
	fields: CodegenField[];
}

export async function generateCode(payload: CodegenPayload): Promise<void> {
	const res = await authorizedFetch(`${API_BASE_URL}/api/tools/codegen`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify(payload)
	});
	const contentType = res.headers.get('content-type') ?? '';
	if (!res.ok || contentType.includes('application/json')) {
		let message = '生成失败';
		try {
			const body = (await res.json()) as { message?: string };
			if (body?.message) message = body.message;
		} catch {
			/* ignore */
		}
		throw new Error(message);
	}
	const blob = await res.blob();
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url;
	a.download = 'codegen.zip';
	a.click();
	URL.revokeObjectURL(url);
}
