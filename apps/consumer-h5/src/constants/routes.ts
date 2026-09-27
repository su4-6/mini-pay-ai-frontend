export const ROUTES = {
  chat: '/',
  login: '/login',
  wallet: '/wallet',
  bills: '/bills',
  collect: '/collect',
  transfer: '/transfer',
  transferResult: '/transfer/result',
  pay: '/pay',
  transfers: '/transfers',
  transferDetail: '/transfers/detail',
  payPassword: '/pay-password',
  me: '/me'
} as const;

export type AppRoute = (typeof ROUTES)[keyof typeof ROUTES];
