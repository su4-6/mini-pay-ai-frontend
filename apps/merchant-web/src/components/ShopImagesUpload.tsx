import { useEffect, useMemo, useState } from 'react';
import { App, Button, Image, Space, Typography, Upload } from 'antd';
import { DeleteOutlined, LoadingOutlined, PlusOutlined } from '@ant-design/icons';
import { merchantApi } from '../services/merchant';

interface ShopImagesUploadProps {
  value?: string;
  onChange?: (value: string) => void;
  disabled?: boolean;
}

const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const MAX_BYTES = 5 * 1024 * 1024;

function isHttpUrl(value: string): boolean {
  return /^https?:\/\//i.test(value);
}

function splitImages(value?: string): string[] {
  if (!value) {
    return [];
  }
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

/**
 * 店铺图片上传：浏览器同源提交给 BFF，再由 BFF 上传 OSS，避免本地开发受 OSS CORS 限制。
 * value 为逗号分隔的 objectKey/URL 列表（与旧格式兼容）。
 */
export default function ShopImagesUpload({
  value,
  onChange,
  disabled
}: ShopImagesUploadProps) {
  const { message } = App.useApp();
  const [uploading, setUploading] = useState(false);
  const [signedUrls, setSignedUrls] = useState<Record<string, string>>({});
  const items = useMemo(() => splitImages(value), [value]);

  // objectKey → 签名 URL（HTTP URL 直接可用，不需要签名）
  useEffect(() => {
    const keys = items.filter((item) => !isHttpUrl(item));
    if (!keys.length) {
      setSignedUrls({});
      return;
    }
    let cancelled = false;
    merchantApi.getImageReadUrls(keys)
      .then(({ urls }) => {
        if (!cancelled) {
          setSignedUrls(urls);
        }
      })
      .catch(() => {
        // 签名失败保留原 objectKey 展示，不阻塞表单
      });
    return () => {
      cancelled = true;
    };
  }, [value]);

  const uploadOne = async (file: File): Promise<string> => {
    const uploaded = await merchantApi.uploadImageFile(file);
    return uploaded.objectKey;
  };

  const handleUpload = async (file: File) => {
    if (items.length >= 5) {
      void message.error('店铺照片最多 5 张');
      return;
    }
    if (!IMAGE_TYPES.has(file.type)) {
      void message.error('仅支持 JPG / PNG / WebP 图片');
      return;
    }
    if (file.size > MAX_BYTES) {
      void message.error('图片不能超过 5MB');
      return;
    }
    setUploading(true);
    try {
      const objectKey = await uploadOne(file);
      onChange?.([...items, objectKey].join(','));
    } catch (error) {
      void message.error(
        error instanceof Error ? error.message : '图片上传失败，请稍后重试'
      );
    } finally {
      setUploading(false);
    }
  };

  return (
    <Space direction="vertical" size={8}>
      <Space size={8} wrap>
      {items.map((item) => (
        <div
          key={item}
          style={{
            position: 'relative',
            width: 96,
            height: 96,
            borderRadius: 8,
            overflow: 'hidden',
            border: '1px solid #d9d9d9'
          }}
        >
          <Image
            src={isHttpUrl(item) ? item : (signedUrls[item] ?? item)}
            alt="店铺图片"
            style={{ width: 96, height: 96, objectFit: 'cover' }}
            preview={Boolean(isHttpUrl(item) || signedUrls[item])}
          />
          {isHttpUrl(item) ? (
            <Typography.Text
              style={{
                position: 'absolute',
                bottom: 0,
                left: 0,
                right: 0,
                padding: '2px 4px',
                fontSize: 10,
                lineHeight: '14px',
                textAlign: 'center',
                color: '#fff',
                background: 'rgba(0,0,0,0.55)'
              }}
            >
              外部图片
            </Typography.Text>
          ) : null}
          {!disabled && !isHttpUrl(item) ? (
            <Button
              type="text"
              danger
              size="small"
              icon={<DeleteOutlined />}
              style={{
                position: 'absolute',
                top: 0,
                right: 0,
                background: 'rgba(255,255,255,0.85)'
              }}
              onClick={() => onChange?.(items.filter((entry) => entry !== item).join(','))}
            />
          ) : null}
        </div>
      ))}
      {!disabled && items.length < 5 ? (
        <Upload
          accept="image/jpeg,image/png,image/webp"
          showUploadList={false}
          multiple={false}
          disabled={uploading}
          beforeUpload={(file) => {
            void handleUpload(file as File);
            return false;
          }}
        >
          <Button
            style={{ width: 96, height: 96 }}
            icon={uploading ? <LoadingOutlined /> : <PlusOutlined />}
          >
            {uploading ? '上传中' : '上传'}
          </Button>
        </Upload>
      ) : null}
      </Space>
      <Typography.Text type="secondary">
        支持 JPG、PNG、WebP；单张不超过 5MB，最多上传 5 张
      </Typography.Text>
    </Space>
  );
}
