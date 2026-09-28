import { delay } from '$lib/mock/_helpers';
import type { CodegenPayload } from '../real/codegen';

export type { CodegenField, CodegenPayload } from '../real/codegen';

export async function generateCode(_payload: CodegenPayload): Promise<void> {
	await delay(200);
}
