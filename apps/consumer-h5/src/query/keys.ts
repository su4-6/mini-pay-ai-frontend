/**
 * TanStack Query key 工厂。
 * 写操作成功后只按这些精确 key（或其明确前缀）失效，禁止无差别刷新全部缓存。
 */
export const queryKeys = {
  session: ['session'] as const,

  /** 钱包家族前缀：失效它即同时刷新余额与账单（同一资源族）。 */
  walletRoot: ['wallet'] as const,
  wallet: ['wallet', 'summary'] as const,
  billPreview: ['wallet', 'bills', 'preview'] as const,
  billInfinite: ['wallet', 'bills', 'infinite'] as const,

  collectionCode: ['collection-code'] as const,
  merchantCenter: ['merchant-center'] as const,
  businessCollectionCode: ['merchant-center', 'collection-code'] as const,
  bankCards: ['bank-cards'] as const,
  profile: ['profile'] as const,
  capabilities: ['capabilities'] as const,
  accountSecurity: ['account-security'] as const,
  fundingRoot: ['funding-orders'] as const,
  fundingOrders: (type: 'RECHARGE' | 'WITHDRAWAL') => ['funding-orders', type] as const,

  /** 转账单家族前缀。 */
  transfersRoot: ['transfers'] as const,
  transferInfinite: ['transfers', 'list'] as const,
  transferDetail: (transferNo: string) => ['transfers', 'detail', transferNo] as const
} as const;

export const aiQueryKeys = {
  conversations: ['ai', 'conversations'] as const,
  messages: (conversationId: string) => ['ai', 'conversations', conversationId, 'messages'] as const
} as const;
