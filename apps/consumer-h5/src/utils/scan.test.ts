import { describe, expect, it } from 'vitest';
import { extractPaymentCode } from './scan';

const TOKEN = 'mc_01a0e281fc2878259f04d0f2225cffdeZUGZuVINRs_GX9pehRcpDD8QpICXbJ3c';

describe('extractPaymentCode', () => {
  it('接受商户门户展示的深链', () => {
    expect(extractPaymentCode(`minipay://collect/merchant?token=${TOKEN}`)).toBe(`minipay://collect/merchant?token=${TOKEN}`);
  });

  it('接受只复制了令牌的裸串', () => {
    expect(extractPaymentCode(TOKEN)).toBe(TOKEN);
    expect(extractPaymentCode(`  ${TOKEN}  `)).toBe(TOKEN);
  });

  it('接受带 token 参数的第三方链接', () => {
    expect(extractPaymentCode(`https://pay.su46proj.site/c?token=${TOKEN}&from=poster`)).toBe(`https://pay.su46proj.site/c?token=${TOKEN}&from=poster`);
  });

  it('参数被百分号编码时先解码', () => {
    expect(extractPaymentCode(`minipay://collect/merchant?token=${encodeURIComponent(TOKEN)}`)).toBe(`minipay://collect/merchant?token=${encodeURIComponent(TOKEN)}`);
  });

  it('个人收款码也解析出令牌，交给后端给出精确提示', () => {
    // 页面据此提示「这是个人收款码，请改用转账」，比笼统的格式错误更有用。
    expect(extractPaymentCode(`minipay://collect/personal?token=${TOKEN}`)).toBe(`minipay://collect/personal?token=${TOKEN}`);
    expect(extractPaymentCode('minipay://collect/personal?token=djE6MDFhMGViMmEtM2Q0MC00')).toBe('minipay://collect/personal?token=djE6MDFhMGViMmEtM2Q0MC00');
  });

  it('空值与非收款码文本返回 null', () => {
    expect(extractPaymentCode('')).toBeNull();
    expect(extractPaymentCode('   ')).toBeNull();
    expect(extractPaymentCode(null)).toBeNull();
    expect(extractPaymentCode(undefined)).toBeNull();
    expect(extractPaymentCode('minipay://collect/merchant')).toBeNull();
    expect(extractPaymentCode('https://example.com/pay?token=')).toBeNull();
    expect(extractPaymentCode('随便一段文字 mc_')).toBeNull();
    expect(extractPaymentCode('https://example.com/pay?token=nope')).toBeNull();
    // 长度不足的令牌形状不合法，直接拒绝，避免把明显不是收款码的东西提交给后端。
    expect(extractPaymentCode('https://example.com/pay?token=mc_short')).toBeNull();
    expect(extractPaymentCode(`${TOKEN}!`)).toBeNull();
  });
});
