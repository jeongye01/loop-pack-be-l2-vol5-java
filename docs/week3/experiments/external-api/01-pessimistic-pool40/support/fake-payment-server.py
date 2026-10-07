"""Test-only external payment double used outside the application transaction."""
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get('FAKE_PAYMENT_PORT', '18081'))
state = {'authorized': {}, 'cancelled': set(), 'authorize': 0, 'cancel': 0, 'duplicates': 0}
lock = threading.Lock()


def reply(handler, status, body):
    payload = json.dumps(body).encode()
    handler.send_response(status)
    handler.send_header('Content-Type', 'application/json')
    handler.send_header('Content-Length', str(len(payload)))
    handler.end_headers()
    handler.wfile.write(payload)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        return

    def do_GET(self):
        if self.path == '/payments/stats':
            with lock:
                reply(self, 200, {'authorized': len(state['authorized']), 'cancelled': len(state['cancelled']),
                                  'authorizeCalls': state['authorize'], 'cancelCalls': state['cancel'],
                                  'duplicates': state['duplicates']})
            return
        reply(self, 404, {'error': 'NOT_FOUND'})

    def do_POST(self):
        length = int(self.headers.get('Content-Length', '0'))
        body = json.loads(self.rfile.read(length) or '{}')
        key = body.get('idempotencyKey')
        order_id = str(body.get('orderId'))
        if self.path == '/payments/authorize':
            delay = max(0, int(body.get('delayMs', 300))) / 1000
            time.sleep(delay)
            with lock:
                state['authorize'] += 1
                if key in state['authorized']:
                    state['duplicates'] += 1
                else:
                    state['authorized'][key] = order_id
            reply(self, 200, {'status': 'AUTHORIZED', 'orderId': order_id, 'idempotencyKey': key})
            return
        if self.path == '/payments/cancel':
            with lock:
                state['cancel'] += 1
                if key in state['authorized']:
                    state['cancelled'].add(key)
            reply(self, 200, {'status': 'CANCELLED', 'orderId': order_id, 'idempotencyKey': key})
            return
        reply(self, 404, {'error': 'NOT_FOUND'})


if __name__ == '__main__':
    print(f'Fake payment server ready on {PORT}', flush=True)
    ThreadingHTTPServer(('127.0.0.1', PORT), Handler).serve_forever()
