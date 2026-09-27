/**
 * 扫码结果解析：只负责把「扫到 / 粘到的文本」变成可提交的收款码，不发任何请求。
 *
 * 单独成文件的原因：相机扫到的内容有三种常见形态，都必须识别
 *   1. 我们自己的深链 `minipay://collect/merchant?token=mc_xxx`（商户门户展示的就是它）
 *   2. 裸令牌 `mc_xxx`（有人只复制了 token 那一段）
 *   3. 带 `token` 查询参数的 http(s) 链接（第三方生成的二维码）
 *
 * 个人收款码的深链同样会解析出 token —— 这是刻意的：交给服务端判定后，
 * 页面能给出「这是个人收款码，请改用转账」这种精确提示，而不是笼统的格式错误。
 * 金额与收款方一律以后端返回为准，这里绝不做任何本地推导。
 */

/** 商户收款深链前缀。 */
export const MERCHANT_CODE_SCHEME = 'minipay://collect/merchant';

/** 裸令牌：`mc_` + 一串 URL 安全字符（与后端签发的格式一致）。 */
const BARE_TOKEN = /^mc_[A-Za-z0-9_-]{8,}$/;

/** 深链或链接里的 `token` 查询参数。 */
const TOKEN_PARAM = /[?&]token=([^&#\s]+)/;

/** 相机扫码不可用时的统一提示（UI 与文案只此一处）。 */
export const CAMERA_UNSUPPORTED_HINT =
  '当前浏览器不支持相机扫码（需要 HTTPS + BarcodeDetector），请改为粘贴收款码内容。';

function decodeOnce(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

/**
 * 从任意扫码文本里取出可提交的收款码；取不到返回 `null`。
 *
 * - 深链与裸令牌原样透传（后端按同一套规则校验）
 * - 只带 `token=` 的普通链接抽取该参数
 * - 空串、纯文本、参数为空都返回 `null`，由调用方给出提示
 */
export function extractPaymentCode(raw: string | null | undefined): string | null {
  const text = (raw ?? '').trim();
  if (!text) return null;
  if (BARE_TOKEN.test(text)) return text;

  const matched = TOKEN_PARAM.exec(text);
  if (!matched) return null;
  const token = decodeOnce(matched[1]).trim();
  return BARE_TOKEN.test(token) ? token : null;
}

/**
 * 当前环境是否具备相机扫码能力：HTTPS 下的 `getUserMedia` + 浏览器自带 `BarcodeDetector`。
 *
 * 不引入任何解码库是刻意的：演示端只在移动端 Chrome / 新版 Safari 上扫码，
 * 这两个环境都自带 `BarcodeDetector`；不支持的浏览器退化为「粘贴收款码」即可。
 */
export function isCameraScanSupported(): boolean {
  if (typeof navigator === 'undefined' || typeof window === 'undefined') return false;
  const hasCamera = typeof navigator.mediaDevices?.getUserMedia === 'function';
  const Detector = (window as { BarcodeDetector?: unknown }).BarcodeDetector;
  return hasCamera && typeof Detector === 'function';
}
