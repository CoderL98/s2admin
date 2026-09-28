import { USE_MOCK } from '$lib/config';
import * as mock from './mock/tenant';
import * as real from './real/tenant';

export const getTenantList = USE_MOCK ? mock.getTenantList : real.getTenantList;
export const getTenantOptions = USE_MOCK ? mock.getTenantOptions : real.getTenantOptions;
export const createTenant = USE_MOCK ? mock.createTenant : real.createTenant;
export const updateTenant = USE_MOCK ? mock.updateTenant : real.updateTenant;
export const removeTenant = USE_MOCK ? mock.removeTenant : real.removeTenant;
