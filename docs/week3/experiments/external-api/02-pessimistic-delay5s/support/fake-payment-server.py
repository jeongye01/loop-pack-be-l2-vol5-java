"""Async test-only payment double; it never runs inside the application transaction."""
import asyncio
import json
import os

PORT = int(os.environ.get('FAKE_PAYMENT_PORT', '18081'))
state = {'authorized': {}, 'cancelled': set(), 'authorize': 0, 'cancel': 0, 'duplicates': 0}
state_lock = asyncio.Lock()


async def response(writer, status, body):
    payload = json.dumps(body).encode()
    writer.write((f'HTTP/1.1 {status} OK\r\nContent-Type: application/json\r\n'
                  f'Content-Length: {len(payload)}\r\nConnection: close\r\n\r\n').encode() + payload)
    await writer.drain()
    writer.close()
    await writer.wait_closed()


async def read_request(reader):
    header = await reader.readuntil(b'\r\n\r\n')
    lines = header.decode().split('\r\n')
    method, path, _ = lines[0].split(' ', 2)
    headers = {line.split(':', 1)[0].lower(): line.split(':', 1)[1].strip()
               for line in lines[1:] if ':' in line}
    length = int(headers.get('content-length', '0'))
    body = json.loads((await reader.readexactly(length)).decode() or '{}') if length else {}
    return method, path, body


async def handle(reader, writer):
    try:
        method, path, body = await read_request(reader)
        if method == 'GET' and path == '/payments/stats':
            async with state_lock:
                await response(writer, 200, {'authorized': len(state['authorized']),
                    'cancelled': len(state['cancelled']), 'authorizeCalls': state['authorize'],
                    'cancelCalls': state['cancel'], 'duplicates': state['duplicates']})
            return
        key = body.get('idempotencyKey')
        order_id = str(body.get('orderId'))
        if method == 'POST' and path == '/payments/authorize':
            await asyncio.sleep(max(0, int(body.get('delayMs', 5000))) / 1000)
            async with state_lock:
                state['authorize'] += 1
                if key in state['authorized']:
                    state['duplicates'] += 1
                else:
                    state['authorized'][key] = order_id
            await response(writer, 200, {'status': 'AUTHORIZED', 'orderId': order_id, 'idempotencyKey': key})
            return
        if method == 'POST' and path == '/payments/cancel':
            async with state_lock:
                state['cancel'] += 1
                if key in state['authorized']:
                    state['cancelled'].add(key)
            await response(writer, 200, {'status': 'CANCELLED', 'orderId': order_id, 'idempotencyKey': key})
            return
        await response(writer, 404, {'error': 'NOT_FOUND'})
    except (asyncio.IncompleteReadError, ConnectionError, json.JSONDecodeError):
        writer.close()
        await writer.wait_closed()


async def main():
    # 600 VU can have hundreds of authorize calls sleeping concurrently.
    # Keep the accept backlog above that concurrency so transport errors do not
    # measure the test double's listen queue instead of the application.
    server = await asyncio.start_server(handle, '127.0.0.1', PORT,
                                        limit=1024 * 1024, backlog=2048)
    print(f'Fake payment server ready on {PORT}', flush=True)
    async with server:
        await server.serve_forever()


if __name__ == '__main__':
    asyncio.run(main())
