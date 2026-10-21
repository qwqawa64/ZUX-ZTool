#!/usr/bin/env node
// Samples CSS/Web animations from a real page so a web implementation can be ported to
// Canvas instead of hand-transcribing formulas. See README.md for the workflow.

import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';

const CHROME_CANDIDATES = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
];

function parseArgs(argv) {
  const args = { command: argv[0] };
  for (let i = 1; i < argv.length; i += 2) {
    if (!argv[i].startsWith('--')) throw new Error(`unexpected argument: ${argv[i]}`);
    args[argv[i].slice(2)] = argv[i + 1];
  }
  return args;
}

function findChrome(explicit) {
  const candidates = explicit ? [explicit] : CHROME_CANDIDATES;
  for (const candidate of candidates) {
    if (existsSync(candidate)) return candidate;
  }
  throw new Error('no Chrome/Edge found; pass --chrome <path>');
}

function toUrl(value) {
  if (/^[a-z]+:\/\//i.test(value)) return value;
  return pathToFileURL(resolve(value)).href;
}

/** Minimal CDP client: one browser websocket, flattened sessions. */
class Cdp {
  constructor(ws) {
    this.ws = ws;
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Set();
    ws.addEventListener('message', (event) => {
      const message = JSON.parse(event.data);
      if (message.id && this.pending.has(message.id)) {
        const { resolve: done, reject } = this.pending.get(message.id);
        this.pending.delete(message.id);
        if (message.error) reject(new Error(`${message.error.message} (${message.error.code})`));
        else done(message.result);
        return;
      }
      for (const listener of this.listeners) listener(message);
    });
  }

  static async launch(chromePath, extraArgs = []) {
    const userDataDir = join(process.env.TEMP || '/tmp', `webframes-${process.pid}`);
    const child = spawn(chromePath, [
      '--headless=new',
      '--remote-debugging-port=0',
      `--user-data-dir=${userDataDir}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-extensions',
      '--hide-scrollbars',
      '--allow-file-access-from-files',
      '--force-color-profile=srgb',
      'about:blank',
      ...extraArgs,
    ], { stdio: ['ignore', 'ignore', 'pipe'] });

    const endpoint = await new Promise((done, fail) => {
      let buffer = '';
      const onData = (chunk) => {
        buffer += chunk.toString();
        const match = buffer.match(/DevTools listening on (ws:\/\/\S+)/);
        if (match) done(match[1]);
      };
      child.stderr.on('data', onData);
      child.once('exit', (code) => fail(new Error(`chrome exited early (${code})`)));
      setTimeout(() => fail(new Error('chrome did not report a DevTools endpoint')), 20000);
    });

    const ws = new WebSocket(endpoint);
    await new Promise((done, fail) => {
      ws.addEventListener('open', done, { once: true });
      ws.addEventListener('error', fail, { once: true });
    });
    const cdp = new Cdp(ws);
    cdp.process = child;
    return cdp;
  }

  send(method, params = {}, sessionId) {
    const id = this.nextId++;
    const payload = { id, method, params };
    if (sessionId) payload.sessionId = sessionId;
    this.ws.send(JSON.stringify(payload));
    return new Promise((done, reject) => this.pending.set(id, { resolve: done, reject }));
  }

  waitFor(method, timeoutMs = 15000) {
    return new Promise((done, fail) => {
      const listener = (message) => {
        if (message.method !== method) return;
        clearTimeout(timer);
        this.listeners.delete(listener);
        done(message.params);
      };
      const timer = setTimeout(() => {
        this.listeners.delete(listener);
        fail(new Error(`timed out waiting for ${method}`));
      }, timeoutMs);
      this.listeners.add(listener);
    });
  }

  close() {
    try { this.ws.close(); } catch { /* already closed */ }
    this.process?.kill();
  }
}

/** Opens `url`, applies a viewport and returns the page session id. */
async function openPage(cdp, url, width, height, scale) {
  const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
  await cdp.send('Page.enable', {}, sessionId);
  await cdp.send('Emulation.setDeviceMetricsOverride', {
    width, height, deviceScaleFactor: scale, mobile: false,
  }, sessionId);
  const loaded = cdp.waitFor('Page.loadEventFired');
  await cdp.send('Page.navigate', { url }, sessionId);
  await loaded;
  return { sessionId, targetId };
}

async function evaluate(cdp, sessionId, expression) {
  const result = await cdp.send('Runtime.evaluate', {
    expression, returnByValue: true, awaitPromise: true,
  }, sessionId);
  if (result.exceptionDetails) {
    throw new Error(`page error: ${result.exceptionDetails.text}`);
  }
  return result.result.value;
}

/**
 * Pauses every animation on the page and seeks it to `ms`. `getAnimations()` covers CSS
 * animations, so this works without touching the page's own JS.
 */
function seekExpression(ms) {
  return `(() => {
    const animations = document.getAnimations();
    for (const animation of animations) {
      animation.pause();
      animation.currentTime = ${ms};
    }
    return animations.length;
  })()`;
}

const ELEMENT_BOX = (selector) => `(() => {
  const element = document.querySelector(${JSON.stringify(selector)});
  if (!element) return null;
  const rect = element.getBoundingClientRect();
  return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };
})()`;

const SPEC_EXPRESSION = (selector) => `(() => {
  const filter = ${JSON.stringify(selector)};
  const matches = (animation) => {
    if (!filter) return true;
    const target = animation.effect?.target;
    return !!target && (target.matches?.(filter) || target.closest?.(filter) != null);
  };
  return document.getAnimations().filter(matches).map((animation) => {
    const timing = animation.effect.getTiming();
    return {
      animationName: animation.animationName || null,
      target: animation.effect?.target?.className || null,
      timing: {
        duration: timing.duration,
        delay: timing.delay,
        endDelay: timing.endDelay,
        easing: timing.easing,
        iterations: timing.iterations === Infinity ? 'infinite' : timing.iterations,
        direction: timing.direction,
        fill: timing.fill,
      },
      keyframes: animation.effect.getKeyframes(),
    };
  });
})()`;

async function runSpec(cdp, args) {
  const url = toUrl(args.url);
  const { sessionId } = await openPage(cdp, url, 800, 800, 1);
  if (args.settle) {
    await new Promise((done) => setTimeout(done, Number(args.settle)));
  }
  const spec = await evaluate(cdp, sessionId, SPEC_EXPRESSION(args.selector));
  process.stdout.write(`${JSON.stringify({ url: args.url, selector: args.selector || null, animations: spec }, null, 2)}\n`);
}

async function runFrames(cdp, args) {
  const url = toUrl(args.url);
  const selector = args.selector || 'body';
  const scale = Number(args.scale || 2);
  const times = args.times
    ? args.times.split(',').map((value) => Number(value.trim()))
    : Array.from({ length: Number(args.count || 8) }, (_, index) =>
        Math.round((index * Number(args.period || 1000)) / Number(args.count || 8)));
  const outDir = resolve(args.out || 'frames');
  mkdirSync(outDir, { recursive: true });

  const { sessionId } = await openPage(
    cdp, url, Number(args.width || 900), Number(args.height || 500), scale);
  if (args.settle) {
    await new Promise((done) => setTimeout(done, Number(args.settle)));
  }
  const box = await evaluate(cdp, sessionId, ELEMENT_BOX(selector));
  if (!box) throw new Error(`selector matched nothing: ${selector}`);
  const clip = {
    x: Math.max(0, box.x),
    y: Math.max(0, box.y),
    width: box.width,
    height: box.height,
    scale,
  };

  const frames = [];
  for (const [index, time] of times.entries()) {
    await evaluate(cdp, sessionId, seekExpression(time));
    const shot = await cdp.send('Page.captureScreenshot', {
      format: 'png', fromSurface: true, clip,
    }, sessionId);
    const name = `frame_${String(index).padStart(3, '0')}.png`;
    writeFileSync(join(outDir, name), Buffer.from(shot.data, 'base64'));
    frames.push({ file: name, timeMs: time });
  }

  const manifest = { url: args.url, selector, clip, scale, frames };
  writeFileSync(join(outDir, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
  process.stdout.write(`${outDir}\n`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.command !== 'spec' && args.command !== 'frames') {
    throw new Error('usage: webframes.mjs <spec|frames> --url <page> [options]');
  }
  if (!args.url) throw new Error('--url is required');
  const chromePath = findChrome(args.chrome);
  const cdp = await Cdp.launch(chromePath);
  try {
    if (args.command === 'spec') await runSpec(cdp, args);
    else await runFrames(cdp, args);
  } finally {
    cdp.close();
  }
}

main().catch((error) => {
  console.error(`webframes: ${error.message}`);
  process.exit(1);
});
