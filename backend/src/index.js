import http from 'node:http';
import { loadConfig } from './config/env.js';
import { initFirebaseAdmin } from './config/firebase.js';
import { createBackendApp } from './app.js';
import { attachWebSocketGateway } from './ws/WebSocketGateway.js';

const config = loadConfig();
const { auth, db } = initFirebaseAdmin(config);

const { app, services } = createBackendApp({
  config,
  firebaseAuth: auth,
  db
});

const server = http.createServer(app);
const wss = attachWebSocketGateway({
  httpServer: server,
  config,
  firebaseAuth: auth,
  playerService: services.playerService,
  roomManager: services.roomManager,
  antiCheat: services.antiCheat
});

server.listen(config.port, '0.0.0.0', () => {
  console.log(
    `[CobraDash3D] Authoritative Multiplayer Backend listening on port ${config.port} (maxPlayersPerMatch=${config.maxPlayersPerMatch}, tickRate=${config.tickRateHz}Hz)`
  );
});

function shutdown(signal) {
  console.log(`[CobraDash3D] Received ${signal}, shutting down gracefully...`);
  services.roomManager.shutdown();
  wss.close(() => {
    server.close(() => {
      process.exit(0);
    });
  });
  setTimeout(() => process.exit(1), 8000).unref();
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
