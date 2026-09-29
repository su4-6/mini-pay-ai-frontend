export const ROUTES = {
  home: '/',
  chat: '/miling',
  login: '/login',
  onboarding: '/onboarding',
  wallet: '/wallet',
  bills: '/bills',
  collect: '/collect',
  merchant: '/merchant',
  transfer: '/transfer',
  transferResult: '/transfer/result',
  pay: '/pay',
  transfers: '/transfers',
  transferDetail: '/transfers/detail',
  payPassword: '/pay-password',
  realName: '/real-name',
  bankCards: '/bank-cards',
  funding: '/funding',
  profile: '/profile',
  security: '/security',
  serviceNotice: '/legal/service',
  privacyNotice: '/legal/privacy',
  me: '/me'
} as const;

export type AppRoute = (typeof ROUTES)[keyof typeof ROUTES];
