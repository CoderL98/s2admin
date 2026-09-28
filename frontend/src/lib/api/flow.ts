import { USE_MOCK } from '$lib/config';
import * as mock from './mock/flow';
import * as real from './real/flow';

export const getFlowList = USE_MOCK ? mock.getFlowList : real.getFlowList;
export const getFlowOptions = USE_MOCK ? mock.getFlowOptions : real.getFlowOptions;
export const getFlow = USE_MOCK ? mock.getFlow : real.getFlow;
export const createFlow = USE_MOCK ? mock.createFlow : real.createFlow;
export const updateFlow = USE_MOCK ? mock.updateFlow : real.updateFlow;
export const removeFlow = USE_MOCK ? mock.removeFlow : real.removeFlow;
