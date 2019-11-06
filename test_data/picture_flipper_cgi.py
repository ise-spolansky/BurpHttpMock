#! /usr/bin/env python3
#Example CGI script that downloads the requested image, flips it upside down, and serve the result
#Requires PIL (pip3 install pillow)
#To use, make a Burp HTTP Mock rule that redirects image requests to this script
import sys, os, io
from PIL import Image
import urllib.request, ssl

print('HTTP/1.1 200 OK')
print('Content-Type: image/png')
print(flush=True)

#This disables certificate validation. It works around a bug in the python3 brew install script
#that made it not setup a proper certificate trust store for python on my machine.
#If you use this in production code, you *will* get hacked, and I will laugh at you.
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
