/**
 * 权限管理 API —— 根据 PUBLIC_USE_MOCK 自动切换 mock / 真实后端
 */
import { USE_MOCK } from '$lib/config';
import * as mock from './mock/permission';
import * as real from './real/permission';

export const getPermissionList = USE_MOCK ? mock.getPermissionList : real.getPermissionList;
export const getPermissionTree = USE_MOCK ? mock.getPermissionTree : real.getPermissionTree;
export const getAllPermissions = USE_MOCK ? mock.getAllPermissions : real.getAllPermissions;
export const createPermission = USE_MOCK ? mock.createPermission : real.createPermission;
export const updatePermission = USE_MOCK ? mock.updatePermission : real.updatePermission;
export const removePermission = USE_MOCK ? mock.removePermission : real.removePermission;
