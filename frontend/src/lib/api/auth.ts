/**
 * 认证 API —— 根据 PUBLIC_USE_MOCK 自动切换 mock / 真实后端
 */
import { USE_MOCK } from '$lib/config';
import * as mock from './mock/auth';
import * as real from './real/auth';

export const login = USE_MOCK ? mock.login : real.login;
export const logout = USE_MOCK ? mock.logout : real.logout;
export const getUserInfo = USE_MOCK ? mock.getUserInfo : real.getUserInfo;
export const refresh = USE_MOCK ? mock.refresh : real.refresh;
export const getUserMenus = USE_MOCK ? mock.getUserMenus : real.getUserMenus;
export const getCaptcha = USE_MOCK ? mock.getCaptcha : real.getCaptcha;
export const oauthProviders = USE_MOCK ? mock.oauthProviders : real.oauthProviders;
export const updateProfile = USE_MOCK ? mock.updateProfile : real.updateProfile;
export const changePassword = USE_MOCK ? mock.changePassword : real.changePassword;
export const forgotPassword = USE_MOCK ? mock.forgotPassword : real.forgotPassword;
export const resetPassword = USE_MOCK ? mock.resetPassword : real.resetPassword;
export const uploadAvatar = USE_MOCK ? mock.uploadAvatar : real.uploadAvatar;
export const getMySessions = USE_MOCK ? mock.getMySessions : real.getMySessions;
export const kickSession = USE_MOCK ? mock.kickSession : real.kickSession;
export const kickOtherSessions = USE_MOCK ? mock.kickOtherSessions : real.kickOtherSessions;
export const getMyLoginLogs = USE_MOCK ? mock.getMyLoginLogs : real.getMyLoginLogs;
export const getMyOperations = USE_MOCK ? mock.getMyOperations : real.getMyOperations;
