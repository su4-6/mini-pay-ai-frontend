import { Button, Toast } from 'antd-mobile';
import { useEffect, useRef, useState } from 'react';
import { CAMERA_UNSUPPORTED_HINT, isCameraScanSupported } from '../utils/scan';
import styles from './scan-code.module.less';

export interface ScanCodeButtonProps {
  /** 识别到二维码后回调原始文本；解析交给 `extractPaymentCode`，这里不做业务判断。 */
  onDetected: (text: string) => void;
  disabled?: boolean;
}

/** 浏览器自带条码识别 API 的最小类型（TS 标准库未收录）。 */
interface BarcodeDetectorLike {
  detect: (source: HTMLVideoElement) => Promise<Array<{ rawValue?: string }>>;
}
type BarcodeDetectorCtor = new (options?: { formats?: string[] }) => BarcodeDetectorLike;

/**
 * 相机扫码按钮：复用浏览器自带的 `BarcodeDetector`，不引入任何解码依赖。
 *
 * 隐私与资源：只有用户点开才申请摄像头；识别到第一帧或关闭浮层时立刻
 * `stop()` 所有轨道，绝不让摄像头在后台常驻。不支持的浏览器退化为提示文案，
 * 用户仍可用「粘贴收款码内容」这条一直存在的路径。
 */
export function ScanCodeButton({ onDetected, disabled }: ScanCodeButtonProps) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  // 回调放进 ref：避免父组件每次 render 都换函数引用，导致相机被反复重启。
  const onDetectedRef = useRef(onDetected);
  useEffect(() => {
    onDetectedRef.current = onDetected;
  }, [onDetected]);

  useEffect(() => {
    if (!open) return undefined;
    let closed = false;
    let timer: ReturnType<typeof setInterval> | undefined;

    const release = () => {
      if (timer) {
        clearInterval(timer);
        timer = undefined;
      }
      streamRef.current?.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    };

    const scan = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment' },
          audio: false
        });
        if (closed) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        streamRef.current = stream;
        const video = videoRef.current;
        if (!video) return;
        video.srcObject = stream;
        await video.play();

        const Detector = (window as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector;
        if (!Detector) return;
        const detector = new Detector({ formats: ['qr_code'] });
        timer = setInterval(() => {
          void detector
            .detect(video)
            .then((codes) => {
              const value = codes.find((code) => code.rawValue)?.rawValue;
              if (!value || closed) return;
              closed = true;
              release();
              setOpen(false);
              onDetectedRef.current(value);
            })
            // 单帧识别失败（画面模糊等）属正常情况，继续下一帧即可。
            .catch(() => undefined);
        }, 350);
      } catch (cause) {
        if (closed) return;
        const denied = cause instanceof Error && cause.name === 'NotAllowedError';
        setError(
          denied
            ? '未获得摄像头权限：请在浏览器地址栏的权限设置里允许后重试。'
            : '无法打开摄像头，请改用粘贴收款码内容。'
        );
      }
    };

    void scan();
    return () => {
      closed = true;
      release();
    };
  }, [open]);

  if (!isCameraScanSupported()) {
    return (
      <Button
        block
        fill="outline"
        disabled={disabled}
        onClick={() => {
          Toast.show({ content: CAMERA_UNSUPPORTED_HINT });
        }}
      >
        扫一扫（当前浏览器不支持，可粘贴收款码）
      </Button>
    );
  }

  return (
    <>
      <Button
        block
        fill="outline"
        disabled={disabled}
        onClick={() => {
          setError(null);
          setOpen(true);
        }}
      >
        扫一扫商户收款码
      </Button>

      {open ? (
        <div className={styles.overlay} role="dialog" aria-label="相机扫码">
          <video ref={videoRef} className={styles.video} playsInline muted />
          <p className={styles.hint}>{error ?? '把商户收款码放进取景框，识别成功会自动识别收款方'}</p>
          <Button block color="primary" onClick={() => setOpen(false)}>
            关闭
          </Button>
        </div>
      ) : null}
    </>
  );
}
