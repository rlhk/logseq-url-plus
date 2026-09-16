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
//   node script/logseq-cdp.mjs type '<text>'     real keystrokes into the focused element
//   node script/logseq-cdp.mjs key <Name>        one named key, e.g. Enter, Escape
//
// `type` and `key` dispatch through the Input domain, so they are real browser
// input events. That matters because Logseq wires slash-command handlers as
// events INSIDE the plugin sandbox - they cannot be fired from the host with
// caller.call - so this is the only way to exercise a slash command without a
// human at the keyboard.
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

/** Open one socket and run a batch of commands over it, in order. */
function withSocket(wsUrl, run, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    let id = 0;
    const pending = new Map();
    const timer = setTimeout(() => {
      ws.close();
      reject(new Error('CDP timeout (batch)'));
    }, timeoutMs);
    const call = (method, params = {}) =>
      new Promise((res, rej) => {
        const myId = ++id;
        pending.set(myId, { res, rej });
        ws.send(JSON.stringify({ id: myId, method, params }));
      });
    ws.onmessage = (m) => {
      const data = JSON.parse(m.data);
      const p = pending.get(data.id);
      if (!p) return;
      pending.delete(data.id);
      data.error ? p.rej(new Error(JSON.stringify(data.error))) : p.res(data.result);
    };
    ws.onerror = () => {
      clearTimeout(timer);
      reject(new Error(`Cannot connect to ${wsUrl}`));
    };
    ws.onopen = async () => {
      try {
        const out = await run(call);
        clearTimeout(timer);
        ws.close();
        resolve(out);
      } catch (e) {
        clearTimeout(timer);
        ws.close();
        reject(e);
      }
    };
  });
}

/** Physical-key metadata for a printable character. */
function keyMeta(ch) {
  const upper = ch.toUpperCase();
  if (ch >= 'a' && ch <= 'z') return { code: `Key${upper}`, windowsVirtualKeyCode: upper.charCodeAt(0) };
  if (ch >= 'A' && ch <= 'Z') return { code: `Key${upper}`, windowsVirtualKeyCode: upper.charCodeAt(0), modifiers: 8 };
  if (ch >= '0' && ch <= '9') return { code: `Digit${ch}`, windowsVirtualKeyCode: ch.charCodeAt(0) };
  const table = {
    '/': { code: 'Slash', windowsVirtualKeyCode: 191 },
    ' ': { code: 'Space', windowsVirtualKeyCode: 32 },
    '.': { code: 'Period', windowsVirtualKeyCode: 190 },
    ',': { code: 'Comma', windowsVirtualKeyCode: 188 },
    '-': { code: 'Minus', windowsVirtualKeyCode: 189 },
    ':': { code: 'Semicolon', windowsVirtualKeyCode: 186, modifiers: 8 },
    '+': { code: 'Equal', windowsVirtualKeyCode: 187, modifiers: 8 },
    '[': { code: 'BracketLeft', windowsVirtualKeyCode: 219 },
    ']': { code: 'BracketRight', windowsVirtualKeyCode: 221 },
    '(': { code: 'Digit9', windowsVirtualKeyCode: 57, modifiers: 8 },
    ')': { code: 'Digit0', windowsVirtualKeyCode: 48, modifiers: 8 },
  };
  return table[ch] ?? {};
}

/** Printable characters need keyDown+char+keyUp to look like real typing. */
async function typeText(call, text) {
  for (const ch of text) {
    // keyDown must NOT carry `text`: both keyDown-with-text and char insert,
    // so sending both types every character twice.
    //
    // `code` and windowsVirtualKeyCode matter: Logseq opens its slash menu from
    // a keydown handler that inspects the key code, so a char event alone
    // inserts the "/" without ever showing the command popup.
    const meta = keyMeta(ch);
    await call('Input.dispatchKeyEvent', { type: 'keyDown', key: ch, ...meta });
    await call('Input.dispatchKeyEvent', { type: 'char', text: ch, key: ch, ...meta });
    await call('Input.dispatchKeyEvent', { type: 'keyUp', key: ch, ...meta });
    // Logseq's slash menu filters on each keystroke; give it a frame.
    await new Promise((r) => setTimeout(r, 25));
  }
}

const NAMED_KEYS = {
  Enter: { key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13, text: '\r' },
  Escape: { key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 },
  Backspace: { key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8 },
  ArrowDown: { key: 'ArrowDown', code: 'ArrowDown', windowsVirtualKeyCode: 40 },
  ArrowUp: { key: 'ArrowUp', code: 'ArrowUp', windowsVirtualKeyCode: 38 },
};

async function pressKey(call, name) {
  const k = NAMED_KEYS[name];
  if (!k) throw new Error(`Unknown key "${name}". Known: ${Object.keys(NAMED_KEYS).join(', ')}`);
  await call('Input.dispatchKeyEvent', { type: 'rawKeyDown', ...k });
  if (k.text) await call('Input.dispatchKeyEvent', { type: 'char', ...k });
  await call('Input.dispatchKeyEvent', { type: 'keyUp', ...k });
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
  } else if (cmd === 'type') {
    const t = await mainTarget();
    await withSocket(t.webSocketDebuggerUrl, (call) => typeText(call, arg ?? ''));
    console.log(`typed ${(arg ?? '').length} chars`);
  } else if (cmd === 'key') {
    const t = await mainTarget();
    await withSocket(t.webSocketDebuggerUrl, (call) => pressKey(call, arg));
    console.log(`pressed ${arg}`);
  } else if (cmd === 'screenshot') {
    console.log(await screenshot(arg || 'logseq.png'));
  } else {
    console.log(
      'usage: logseq-cdp.mjs targets | eval <js> | screenshot <out.png> | ' +
        "type <text> | key <Name>",
    );
    process.exit(2);
  }
} catch (e) {
  console.error('ERROR:', e.message);
  process.exit(1);
}
