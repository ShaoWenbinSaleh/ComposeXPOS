// Expose OrderingMachine web dev server to LAN and allow cross-origin discovery probes.
config.devServer = config.devServer || {};
config.devServer.host = "0.0.0.0";
if (!config.devServer.port) {
  config.devServer.port = 19082;
}
config.devServer.allowedHosts = "all";
config.devServer.headers = Object.assign({}, config.devServer.headers, {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "*",
  "Access-Control-Allow-Private-Network": "true",
});
