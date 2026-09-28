import { USE_MOCK } from '$lib/config';
import * as mock from './mock/codegen';
import * as real from './real/codegen';

export const generateCode = USE_MOCK ? mock.generateCode : real.generateCode;
export type { CodegenField, CodegenPayload } from './real/codegen';
