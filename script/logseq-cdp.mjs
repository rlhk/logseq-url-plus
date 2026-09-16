#!/usr/bin/env node
// Minimal Chrome DevTools Protocol client for driving Logseq's renderer.
//
// Logseq is Electron, so its window is a Chromium renderer. Launched with
// --remote-debugging-port it can be driven directly: evaluate JS in the page,
// side-load a plugin through LSPluginCore, read blocks back, take screenshots.
// That is far more reliable than clicking screen coordinates, because Electron
// exposes almost nothing to macOS accessibility - the whole UI is one opaque
// web view.
//
// Deliberately dependency-free: Node 21+ ships a global WebSocket.
//
// Usage:
//   node script/logseq-cdp.mjs targets
//   node script/logseq-cdp.mjs eval '<javascript>'
//   node script/logseq-cdp.mjs screenshot <out.png>
//
// Env: LOGSEQ_CDP_PORT (default 9223)

const PORT = process.env.LOGSEQ_CDP_PORT || '9223';
const BASE = `http://127.0.0.1:${PORT}`;

async function targets() {
  const res = await fetch(`${BASE}/json/list`);
  return res.json();
}

/** The main Logseq window, as opposed to devtools or plugin iframes. */
async function mainTarget() {
  const list = await targets();
  const page = list.find(
    (t) => t.type === 'page' && !t.url.startsWith('devtools://'),
  );
  if (!page) {
    throw new Error(
      `No page target on port ${PORT}. Is Logseq running with ` +
        `--remote-debugging-port=${PORT}?`,
    );
  }
  return page;
}

/** Send one CDP command and resolve its reply. */
function send(wsUrl, method, params = {}, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    const timer = setTimeout(() => {
      ws.close();
      reject(new Error(`CDP timeout: ${method}`));
    }, timeoutMs);
    ws.onopen = () => ws.send(JSON.stringify({ id: 1, method, params }));
    ws.onmessage = (m) => {
      const data = JSON.parse(m.data);
      if (data.id !== 1) return;
      clearTimeout(timer);
      ws.close();
      data.error ? reject(new Error(JSON.stringify(data.error))) : resolve(data.result);
    };
    ws.onerror = () => {
      clearTimeout(timer);
      reject(new Error(`Cannot connect to ${wsUrl}`));
    };
  });
}

async function evaluate(expression) {
  const t = await mainTarget();
  // awaitPromise so callers can hand us async Logseq API calls directly.
  const r = await send(t.webSocketDebuggerUrl, 'Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  });
  if (r.exceptionDetails) {
    throw new Error(
      r.exceptionDetails.exception?.description ||
        JSON.stringify(r.exceptionDetails),
    );
  }
  return r.result?.value;
}

async function screenshot(out) {
  const t = await mainTarget();
  const r = await send(t.webSocketDebuggerUrl, 'Page.captureScreenshot', {
    format: 'png',
  });
  const { writeFileSync } = await import('node:fs');
  writeFileSync(out, Buffer.from(r.data, 'base64'));
  return out;
}

const [cmd, arg] = process.argv.slice(2);
try {
  if (cmd === 'targets') {
    const list = await targets();
    for (const t of list) console.log(`${t.type}\t${t.title}\t${t.url.slice(0, 90)}`);
  } else if (cmd === 'eval') {
    const v = await evaluate(arg);
    console.log(typeof v === 'string' ? v : JSON.stringify(v, null, 2));
  } else if (cmd === 'screenshot') {
    console.log(await screenshot(arg || 'logseq.png'));
  } else {
    console.log('usage: logseq-cdp.mjs targets | eval <js> | screenshot <out.png>');
    process.exit(2);
  }
} catch (e) {
  console.error('ERROR:', e.message);
  process.exit(1);
}
