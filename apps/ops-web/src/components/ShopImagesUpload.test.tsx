import { App, ConfigProvider } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ShopImagesUpload from './ShopImagesUpload';
import { getImageReadUrls, uploadImageFile } from '../services/ops';

vi.mock('../services/ops', () => ({
  uploadImageFile: vi.fn(),
  getImageReadUrls: vi.fn()
}));

function renderUpload(props: { value?: string; onChange?: (value: string) => void }) {
  const wrapped = ({ children }: { children: ReactNode }) => (
    <ConfigProvider><App>{children}</App></ConfigProvider>
  );
  const result = render(wrapped({ children: <ShopImagesUpload {...props} /> }));
  return {
    fileInput: () => result.container.querySelector('input[type="file"]') as HTMLInputElement
  };
}

describe('ShopImagesUpload', () => {
  beforeEach(() => {
    vi.mocked(getImageReadUrls).mockResolvedValue({ urls: {} });
    vi.mocked(uploadImageFile).mockResolvedValue({ objectKey: 'merchants/images/abc.jpg' });
  });

  it('renders legacy HTTP URLs directly without signing and without delete', async () => {
    renderUpload({ value: 'https://cdn.example.com/shop/1.jpg' });
    const image = await screen.findByRole('img', { name: '店铺图片' });
    expect(image.getAttribute('src')).toBe('https://cdn.example.com/shop/1.jpg');
    expect(getImageReadUrls).not.toHaveBeenCalled();
    // 历史外部图片不可删除，只展示角标
    expect(screen.queryByRole('button', { name: 'delete' })).toBeNull();
    expect(screen.getByText('外部图片')).toBeTruthy();
  });

  it('signs object keys for display', async () => {
    vi.mocked(getImageReadUrls).mockResolvedValue({
      urls: { 'merchants/images/abc.jpg': 'https://bucket.oss/signed-abc' }
    });
    renderUpload({ value: 'merchants/images/abc.jpg' });
    await waitFor(() => expect(getImageReadUrls).toHaveBeenCalledWith(
      ['merchants/images/abc.jpg']
    ));
    const image = await screen.findByRole('img', { name: '店铺图片' });
    expect(image.getAttribute('src')).toBe('https://bucket.oss/signed-abc');
  });

  it('uploads a file through the same-origin BFF and appends the object key', async () => {
    const onChange = vi.fn();
    const { fileInput } = renderUpload({ onChange });

    const file = new File(['shop-data'], 'shop.jpg', { type: 'image/jpeg' });
    await userEvent.setup().upload(fileInput(), file);

    await waitFor(() => expect(uploadImageFile).toHaveBeenCalledWith(file));
    await waitFor(() => expect(onChange).toHaveBeenCalledWith('merchants/images/abc.jpg'));
  });

  it('removes an image from the comma-separated value', async () => {
    const onChange = vi.fn();
    renderUpload({
      value: 'merchants/images/a.jpg,merchants/images/b.jpg',
      onChange
    });
    const removeButtons = await screen.findAllByRole('button', { name: 'delete' });
    expect(removeButtons.length).toBe(2);
    removeButtons[0].click();
    expect(onChange).toHaveBeenCalledWith('merchants/images/b.jpg');
  });

  it('only allows deleting object-key images, not legacy URLs', async () => {
    const onChange = vi.fn();
    renderUpload({
      value: 'https://cdn.example.com/old.jpg,merchants/images/b.jpg',
      onChange
    });
    const removeButtons = await screen.findAllByRole('button', { name: 'delete' });
    expect(removeButtons.length).toBe(1);
    removeButtons[0].click();
    expect(onChange).toHaveBeenCalledWith('https://cdn.example.com/old.jpg');
    expect(screen.getByText('外部图片')).toBeTruthy();
  });
});
