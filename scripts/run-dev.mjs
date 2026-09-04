import { spawn } from 'node:child_process';

const port = process.argv[2];
if (!/^\d{4,5}$/.test(port ?? '')) {
  throw new Error('A valid development port is required');
}

const windows = process.platform === 'win32';
const command = windows ? (process.env.ComSpec || 'cmd.exe') : 'pnpm';
const args = windows ? ['/d', '/s', '/c', 'pnpm exec max dev'] : ['exec', 'max', 'dev'];
const child = spawn(command, args, {
  stdio: 'inherit',
  env: { ...process.env, PORT: port, STRICT_PORT: port }
});

child.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal);
  else process.exit(code ?? 1);
});
