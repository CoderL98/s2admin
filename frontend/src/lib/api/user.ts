/**
 * 用户管理 API —— 根据 PUBLIC_USE_MOCK 自动切换 mock / 真实后端
 */
import { USE_MOCK } from '$lib/config';
import * as mock from './mock/user';
import * as real from './real/user';

export const getUserList = USE_MOCK ? mock.getUserList : real.getUserList;
export const getUserById = USE_MOCK ? mock.getUserById : real.getUserById;
export const createUser = USE_MOCK ? mock.createUser : real.createUser;
export const updateUser = USE_MOCK ? mock.updateUser : real.updateUser;
export const removeUser = USE_MOCK ? mock.removeUser : real.removeUser;
export const batchRemoveUsers = USE_MOCK ? mock.batchRemoveUsers : real.batchRemoveUsers;
export const updateUserStatus = USE_MOCK ? mock.updateUserStatus : real.updateUserStatus;
export const resetUserPassword = USE_MOCK ? mock.resetUserPassword : real.resetUserPassword;
export const exportUsers = USE_MOCK ? mock.exportUsers : real.exportUsers;
export const downloadUserTemplate = USE_MOCK ? mock.downloadUserTemplate : real.downloadUserTemplate;
export const importUsers = USE_MOCK ? mock.importUsers : real.importUsers;
export const batchUpdateUserStatus = USE_MOCK ? mock.batchUpdateUserStatus : real.batchUpdateUserStatus;
export const exportUsersXlsx = USE_MOCK ? mock.exportUsers : real.exportUsersXlsx;
export const importUsersXlsx = USE_MOCK ? mock.importUsers : real.importUsersXlsx;
