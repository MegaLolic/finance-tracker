import http from "node:http";
import https from "node:https";
import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { dirname, extname, isAbsolute, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const directory = resolve(dirname(fileURLToPath(import.meta.url)), "dist");
const host = process.env.HOST || "0.0.0.0";
const port = Number(process.env.PORT || 4173);
const gateway = new URL(process.env.GATEWAY_URL || "http://127.0.0.1:9000");
if (
  !["http:", "https:"].includes(gateway.protocol) ||
  gateway.username ||
  gateway.password
) {
  throw new Error(
    "GATEWAY_URL must be an HTTP(S) URL without embedded credentials",
  );
}
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error("PORT must be an integer between 1 and 65535");
}

const mimeTypes = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
};
const hopByHop = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
]);

function forwardedHeaders(headers) {
  const excluded = new Set(hopByHop);
  for (const name of String(headers.connection || "").split(",")) {
    excluded.add(name.trim().toLowerCase());
  }
  return Object.fromEntries(
    Object.entries(headers).filter(
      ([name]) => !excluded.has(name.toLowerCase()),
    ),
  );
}

function securityHeaders(response) {
  response.setHeader("X-Content-Type-Options", "nosniff");
  response.setHeader("X-Frame-Options", "DENY");
  response.setHeader("Referrer-Policy", "same-origin");
  response.setHeader(
    "Permissions-Policy",
    "camera=(), microphone=(), geolocation=()",
  );
  // Recharts and React use inline style attributes; scripts remain same-origin only.
  response.setHeader(
    "Content-Security-Policy",
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; worker-src 'self'; manifest-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'",
  );
}

function json(response, status, message, method) {
  const body = JSON.stringify({ status, message, errors: {} });
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
  });
  response.end(method === "HEAD" ? undefined : body);
}

function proxy(request, response) {
  const upstreamUrl = new URL(gateway);
  const path = request.url.replace(/^\/api(?=\/|\?|$)/, "");
  const base = gateway.pathname.replace(/\/$/, "");
  const [pathname, ...query] = (path || "/").split("?");
  upstreamUrl.pathname = base + (pathname || "/");
  upstreamUrl.search = query.length ? "?" + query.join("?") : "";
  const headers = forwardedHeaders(request.headers);
  headers.host = upstreamUrl.host;
  delete headers["accept-encoding"];
  const transport = gateway.protocol === "https:" ? https : http;
  const upstream = transport.request(
    upstreamUrl,
    { method: request.method, headers },
    (incoming) => {
      const responseHeaders = forwardedHeaders(incoming.headers);
      responseHeaders["cache-control"] = "no-store";
      responseHeaders.pragma = "no-cache";
      // Never retry a financial write: its outcome may already be committed upstream.
      response.writeHead(incoming.statusCode || 502, responseHeaders);
      incoming.on("error", () => response.destroy());
      incoming.pipe(response);
    },
  );
  upstream.setTimeout(15_000, () =>
    upstream.destroy(new Error("Gateway timeout")),
  );
  upstream.on("error", () => {
    if (!response.headersSent)
      json(
        response,
        502,
        "Сервис временно недоступен. Попробуйте позже.",
        request.method,
      );
    else response.destroy();
  });
  request.on("aborted", () => upstream.destroy());
  request.on("error", () => upstream.destroy());
  response.on("close", () => {
    if (!response.writableEnded) upstream.destroy();
  });
  request.pipe(upstream);
}

async function staticFile(request, response, url) {
  if (!["GET", "HEAD"].includes(request.method)) {
    response.setHeader("Allow", "GET, HEAD");
    return json(response, 405, "Метод не поддерживается.", request.method);
  }
  let pathname;
  try {
    pathname = decodeURIComponent(url.pathname);
  } catch {
    return json(response, 400, "Некорректный адрес.", request.method);
  }
  if (
    pathname.includes("\0") ||
    pathname.includes("\\") ||
    pathname.split("/").some((part) => part.startsWith("."))
  ) {
    return json(response, 404, "Файл не найден.", request.method);
  }
  let file = resolve(directory, "." + pathname);
  const offset = relative(directory, file);
  if (offset.startsWith("..") || isAbsolute(offset))
    return json(response, 404, "Файл не найден.", request.method);
  if (pathname === "/") file = resolve(directory, "index.html");
  let details;
  try {
    details = await stat(file);
  } catch {
    /* SPA navigation is handled below. */
  }
  if (!details?.isFile()) {
    const acceptsHtml = String(request.headers.accept || "").includes(
      "text/html",
    );
    const isNavigation =
      acceptsHtml &&
      !extname(pathname) &&
      !/^\/(assets|icons|api|src)(\/|$)/.test(pathname);
    if (!isNavigation)
      return json(response, 404, "Файл не найден.", request.method);
    file = resolve(directory, "index.html");
    try {
      details = await stat(file);
    } catch {
      return json(
        response,
        503,
        "Сначала соберите интерфейс: npm run build.",
        request.method,
      );
    }
  }
  response.setHeader(
    "Content-Type",
    mimeTypes[extname(file)] || "application/octet-stream",
  );
  response.setHeader("Content-Length", details.size);
  response.setHeader(
    "Cache-Control",
    pathname.startsWith("/assets/")
      ? "public, max-age=31536000, immutable"
      : "no-cache",
  );
  if (request.method === "HEAD") return response.end();
  const stream = createReadStream(file);
  stream.on("error", () => response.destroy());
  stream.pipe(response);
}

const server = http.createServer((request, response) => {
  securityHeaders(response);
  let url;
  try {
    url = new URL(request.url, "http://frontend.local");
  } catch {
    return json(response, 400, "Некорректный адрес.", request.method);
  }
  if (url.pathname === "/api" || url.pathname.startsWith("/api/"))
    return proxy(request, response);
  staticFile(request, response, url).catch(() => {
    if (!response.headersSent)
      json(response, 500, "Не удалось открыть страницу.", request.method);
    else response.destroy();
  });
});
server.requestTimeout = 30_000;
server.headersTimeout = 15_000;
server.on("error", (error) => {
  console.error(
    `Не удалось запустить интерфейс (${error.code || "server error"}).`,
  );
  process.exitCode = 1;
});
server.listen(port, host, () =>
  console.log(`Финанс: http://localhost:${port} (HOST=${host})`),
);
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    server.close();
    server.closeIdleConnections();
  });
}
