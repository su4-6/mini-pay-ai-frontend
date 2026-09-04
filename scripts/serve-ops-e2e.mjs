import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, relative, resolve } from 'node:path';

const root = resolve(process.cwd(), 'apps', 'ops-web', 'dist');
const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml'
};

createServer(async (request, response) => {
  const pathname = decodeURIComponent(new URL(request.url ?? '/', 'http://localhost').pathname);
  let target = resolve(root, `.${pathname}`);
  const pathFromRoot = relative(root, target);
  if (pathFromRoot.startsWith('..') || pathFromRoot.includes(':')) target = join(root, 'index.html');
  try {
    if (!(await stat(target)).isFile()) target = join(root, 'index.html');
  } catch {
    target = join(root, 'index.html');
  }
  const body = await readFile(target);
  response.writeHead(200, {
    'Content-Type': contentTypes[extname(target)] ?? 'application/octet-stream',
    'Cache-Control': 'no-store'
  });
  response.end(request.method === 'HEAD' ? undefined : body);
}).listen(8000, '127.0.0.1');
