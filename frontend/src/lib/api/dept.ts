import { USE_MOCK } from '$lib/config';
import * as mock from './mock/dept';
import * as real from './real/dept';

export const getDeptTree = USE_MOCK ? mock.getDeptTree : real.getDeptTree;
export const getDeptOptions = USE_MOCK ? mock.getDeptOptions : real.getDeptOptions;
export const createDept = USE_MOCK ? mock.createDept : real.createDept;
export const updateDept = USE_MOCK ? mock.updateDept : real.updateDept;
export const removeDept = USE_MOCK ? mock.removeDept : real.removeDept;
