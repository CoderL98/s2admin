export const PASSWORD_HINT = '至少 8 位,须包含大小写字母、数字和特殊字符';

export function isStrongPassword(password: string): boolean {
	return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,64}$/.test(password);
}
