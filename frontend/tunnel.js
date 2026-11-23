import localtunnel from 'localtunnel';

(async () => {
  try {
    const tunnel = await localtunnel({ port: 8000 });
    console.log('TUNNEL_URL:' + tunnel.url);
  } catch (err) {
    console.error('Tunnel error:', err);
  }
})();
