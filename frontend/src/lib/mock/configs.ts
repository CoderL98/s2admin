/**
 * 系统配置 mock 数据 —— 对应 sys_config
 */
import type { SysConfig } from '$lib/types/entities';
export type { SysConfig } from '$lib/types/entities';

const now = new Date().toISOString().slice(0, 19).replace('T', ' ');

export const mockConfigs: SysConfig[] = [
	{ id: 1, configKey: 'sys.user.initPassword', configValue: '123456', configType: 'string', groupCode: 'system', remark: '用户初始密码', createBy: 1, createTime: now },
	{ id: 2, configKey: 'sys.account.captchaEnabled', configValue: 'true', configType: 'boolean', groupCode: 'system', remark: '是否启用验证码', createBy: 1, createTime: now },
	{ id: 3, configKey: 'sys.account.captchaExpiration', configValue: '5', configType: 'number', groupCode: 'system', remark: '验证码有效期(分钟)', createBy: 1, createTime: now },
	{ id: 4, configKey: 'sys.account.passwordMinLength', configValue: '8', configType: 'number', groupCode: 'system', remark: '密码最小长度', createBy: 1, createTime: now },
	{ id: 5, configKey: 'sys.account.lockThreshold', configValue: '5', configType: 'number', groupCode: 'system', remark: '账号锁定阈值', createBy: 1, createTime: now },
	{ id: 6, configKey: 'sys.account.lockDuration', configValue: '30', configType: 'number', groupCode: 'system', remark: '账号锁定时长(分钟)', createBy: 1, createTime: now },
	{ id: 7, configKey: 'sys.theme.primaryColor', configValue: '#10b981', configType: 'string', groupCode: 'theme', remark: '主题色', createBy: 1, createTime: now },
	{ id: 8, configKey: 'sys.theme.mode', configValue: 'light', configType: 'string', groupCode: 'theme', remark: '主题模式', createBy: 1, createTime: now },
	{ id: 9, configKey: 'sys.upload.maxSize', configValue: '10', configType: 'number', groupCode: 'upload', remark: '上传文件最大体积(MB)', createBy: 1, createTime: now },
	{ id: 10, configKey: 'sys.upload.allowedTypes', configValue: 'jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx', configType: 'string', groupCode: 'upload', remark: '允许上传的文件类型', createBy: 1, createTime: now },
	{ id: 11, configKey: 'sys.api.rateLimit', configValue: '100', configType: 'number', groupCode: 'api', remark: 'API 限流(次/分钟)', createBy: 1, createTime: now },
	{ id: 12, configKey: 'sys.api.corsOrigins', configValue: '*', configType: 'string', groupCode: 'api', remark: '允许跨域来源', createBy: 1, createTime: now }
];
