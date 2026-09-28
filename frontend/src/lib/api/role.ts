/**
 * 角色管理 API —— 根据 PUBLIC_USE_MOCK 自动切换 mock / 真实后端
 */
import { USE_MOCK } from '$lib/config';
import * as mock from './mock/role';
import * as real from './real/role';

export const getRoleList = USE_MOCK ? mock.getRoleList : real.getRoleList;
export const getAllRoles = USE_MOCK ? mock.getAllRoles : real.getAllRoles;
export const createRole = USE_MOCK ? mock.createRole : real.createRole;
export const updateRole = USE_MOCK ? mock.updateRole : real.updateRole;
export const removeRole = USE_MOCK ? mock.removeRole : real.removeRole;
export const getRolePermissionIds = USE_MOCK ? mock.getRolePermissionIds : real.getRolePermissionIds;
export const assignRolePermissions = USE_MOCK ? mock.assignRolePermissions : real.assignRolePermissions;
