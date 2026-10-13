package com.jussicodes.music.lx

/**
 * 沙盒内的 `window.lx` 垫片（移植 Melodia 的 LxShim，MIT）。
 *
 * 关键设计：脚本不能自己发网络请求。`lx.request` 把 (id, url, method, headers, body)
 * 交给宿主 [LxPluginEngine.SandboxBridge.httpRequest]，宿主用 OkHttp 真发请求后
 * 再 evaluateJavascript 调 `window.__melodia_handle_http_response` 把结果注回沙盒。
 * 沙盒页面里没有 setTimeout（没有真实计时器），因此这里补一套由宿主驱动的定时器。
 */
object LxShim {

    fun script(env: String): String = """
        (function() {
            var pendingCallbacks = {};
            var reqIdCounter = 0;
            var requestHandler = null;

            if (typeof window.setTimeout !== 'function') {
                var timers = {};
                var timerSeq = 0;
                window.setTimeout = function(fn, ms) {
                    var id = ++timerSeq;
                    timers[id] = { fn: fn, repeat: false, ms: ms || 0 };
                    LxHostBridge.setTimer(id, ms || 0);
                    return id;
                };
                window.setInterval = function(fn, ms) {
                    var id = ++timerSeq;
                    timers[id] = { fn: fn, repeat: true, ms: ms || 0 };
                    LxHostBridge.setTimer(id, ms || 0);
                    return id;
                };
                window.clearTimeout = function(id) { delete timers[id]; };
                window.clearInterval = window.clearTimeout;
                window.__lx_fire_timer = function(id) {
                    var t = timers[id];
                    if (!t) return;
                    if (t.repeat) {
                        LxHostBridge.setTimer(id, t.ms);
                    } else {
                        delete timers[id];
                    }
                    t.fn();
                };
            }

            window.lx = {
                version: "2.0.0",
                env: "$env",
                currentScriptInfo: {
                    name: "",
                    description: "",
                    version: "",
                    author: "",
                    rawScript: ""
                },
                EVENT_NAMES: {
                    request: "request",
                    inited: "inited",
                    updateAlert: "updateAlert"
                },
                request: function(url, opts, callback) {
                    var id = ++reqIdCounter;
                    pendingCallbacks[id] = callback;
                    var options = opts || {};
                    var method = options.method || 'GET';
                    var headers = JSON.stringify(options.headers || {});
                    var body = typeof options.body === 'string' ? options.body : (options.body ? JSON.stringify(options.body) : '');
                    LxHostBridge.httpRequest(id, url, method, headers, body);
                    return function() { delete pendingCallbacks[id]; };
                },
                on: function(event, handler) {
                    if (event === 'request') {
                        requestHandler = handler;
                    }
                    return Promise.resolve();
                },
                send: function(event, data) {
                    LxHostBridge.onEvent(event, JSON.stringify(data || {}));
                    return Promise.resolve();
                },
                utils: {
                    crypto: {
                        md5: function(str) { return LxHostBridge.md5(str); }
                    },
                    buffer: {
                        from: function(data) { return data; },
                        bufToString: function(buf) { return String(buf); }
                    }
                }
            };

            window.__lx_handle_http_response = function(id, err, respJson, body) {
                var cb = pendingCallbacks[id];
                if (!cb) return;
                delete pendingCallbacks[id];
                var resp = null;
                try { resp = respJson ? JSON.parse(respJson) : {}; } catch(e) { resp = {}; }
                var parsedBody = body;
                if (typeof body === 'string') {
                    var trimmed = body.trim();
                    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
                        try { parsedBody = JSON.parse(trimmed); } catch(e) {}
                    }
                }
                resp.body = parsedBody;
                resp.statusCode = resp.statusCode || 200;
                cb(err ? new Error(err) : null, resp, parsedBody);
            };

            window.__lx_resolve_music_url = function(callId, source, musicInfoJson, quality) {
                if (!requestHandler) {
                    LxHostBridge.onResolveResult(callId, null, "脚本未注册 request 处理器");
                    return;
                }
                var musicInfo = (typeof musicInfoJson === 'object') ? musicInfoJson : JSON.parse(musicInfoJson);
                Promise.resolve().then(function() {
                    return requestHandler({
                        source: source,
                        action: 'musicUrl',
                        info: {
                            type: quality,
                            musicInfo: musicInfo
                        }
                    });
                }).then(function(result) {
                    var url = typeof result === 'string' ? result : (result && result.url ? result.url : null);
                    LxHostBridge.onResolveResult(callId, url, null);
                }).catch(function(err) {
                    LxHostBridge.onResolveResult(callId, null, err ? err.message : "脚本异常");
                });
            };
        })();
    """.trimIndent()

    /** 导入脚本时的探测页：只注入垫片，不执行用户脚本。 */
    fun html(env: String = "mobile"): String =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body><script>\n" +
            script(env) + "\n</script></body></html>"
}