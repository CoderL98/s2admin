import { USE_MOCK } from '$lib/config';
import * as mock from './mock/dashboard';
import * as real from './real/dashboard';

export const getDashboardStats = USE_MOCK ? mock.getDashboardStats : real.getDashboardStats;
