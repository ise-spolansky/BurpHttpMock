#! /usr/bin/env python3
import sys, os, io
from PIL import Image
import urllib.request, ssl

print('HTTP/1.1 200 OK')
print('Content-Type: image/png')
print(flush=True)

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
url = 'https://' + os.environ['HTTP_HOST'] + os.environ['REQUEST_URI']
f = urllib.request.urlopen(url, context=ctx)
img = Image.open(f)
out = None
with io.BytesIO() as outf: 
    img.rotate(180).save(outf, format='png')
    out = outf.getvalue()

sys.stdout.buffer.write(out)
