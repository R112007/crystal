const http = require('http');
const fs = require('fs');
const path = require('path');

const port = Number(process.argv[2] || 8765);
const root = path.resolve(process.argv[3] || 'build/libs');

const server = http.createServer((request, response) => {
  const name = decodeURIComponent((request.url || '/').split('?')[0]).replace(/^\/+/, '');
  const target = path.resolve(root, name);
  if (!target.startsWith(root + path.sep) || !fs.existsSync(target) || !fs.statSync(target).isFile()) {
    response.writeHead(404);
    response.end('not found');
    return;
  }
  response.writeHead(200, {
    'Content-Type': 'application/java-archive',
    'Content-Length': fs.statSync(target).size,
  });
  fs.createReadStream(target).pipe(response);
});

server.listen(port, '0.0.0.0', () => {
  console.log(`serving ${root} on 0.0.0.0:${port}`);
});
