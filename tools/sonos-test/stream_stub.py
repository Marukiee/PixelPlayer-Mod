#!/usr/bin/env python3
"""Answers just enough of the Subsonic API to let the emulator's phone player stream a test file."""
import re, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
DATA=open(sys.argv[1],'rb').read()
class H(BaseHTTPRequestHandler):
    def log_message(self,*a): pass
    def do_GET(self):
        if '/rest/stream' in self.path:
            total=len(DATA); start,end=0,total-1; code=200
            r=re.match(r'bytes=(\d+)-(\d*)',self.headers.get('Range',''))
            if r:
                start=int(r[1]); end=int(r[2]) if r[2] else total-1; code=206
            self.send_response(code); self.send_header('Content-Type','audio/flac'); self.send_header('Accept-Ranges','bytes')
            self.send_header('Content-Length',str(end-start+1))
            if code==206: self.send_header('Content-Range',f'bytes {start}-{end}/{total}')
            self.end_headers()
            try: self.wfile.write(DATA[start:end+1])
            except (BrokenPipeError,ConnectionResetError): pass
            return
        b=b'{"subsonic-response":{"status":"ok","version":"1.16.1"}}'
        self.send_response(200); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b)
ThreadingHTTPServer(('127.0.0.1',4533),H).serve_forever()
