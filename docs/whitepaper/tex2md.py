#!/usr/bin/env python3
"""Generate icebridge-whitepaper.md from icebridge-whitepaper.tex (the source of truth).

Usage: python3 tex2md.py   (run from docs/whitepaper after building the PDF, which writes the .aux
file this script reads for section, table and figure numbers). Requires pandoc.
"""
import os, re, subprocess, sys, tempfile

here = os.path.dirname(os.path.abspath(__file__))
tex_path = os.path.join(here, 'icebridge-whitepaper.tex')
aux_path = os.path.join(here, 'icebridge-whitepaper.aux')
out_path = os.path.join(here, 'icebridge-whitepaper.md')
pandoc = os.environ.get('PANDOC', 'pandoc')

# Simplify the LaTeX for pandoc: plain \code, no breakable underscore, tabularx -> tabular,
# drop the \multicolumn footer of the capability table (re-added below).
src = open(tex_path).read()
src = src.replace("\\newcommand{\\code}[1]{\\texttt{\\hyphenchar\\font=`\\-\\relax #1}}", "\\newcommand{\\code}[1]{\\texttt{#1}}")
src = src.replace("\\NewCommandCopy\\origunderscore\\_\n", "").replace("\\DeclareRobustCommand{\\_}{\\origunderscore\\allowbreak}\n", "")
src = re.sub(r'\\begin\{tabularx\}\{\\textwidth\}\{([^}]*)\}', lambda m: '\\begin{tabular}{' + m.group(1).replace('L', 'l').replace('X', 'l') + '}', src)
src = src.replace('\\end{tabularx}', '\\end{tabular}')
src = re.sub(r'\\midrule\n\\multicolumn\{4\}\{l\}\{Presets.*?\n\\multicolumn\{4\}\{l\}\{.*?\\\\\n', '', src, flags=re.S)
src = src.replace('\\begin{center}\\small', '\\begin{center}')
with tempfile.TemporaryDirectory() as tmp:
    pin = os.path.join(tmp, 'in.tex'); pout = os.path.join(tmp, 'raw.md')
    open(pin, 'w').write(src)
    subprocess.run([pandoc, pin, '-f', 'latex', '-t', 'gfm', '--wrap=none', '-o', pout], check=True)
    raw_md = open(pout).read()

tex = open(tex_path).read()
aux = open(aux_path).read()
md = raw_md

labels = {m.group(1): m.group(2) for m in re.finditer(r'\\newlabel\{([^}]*)\}\{\{([^}]*)\}', aux)}

# cross references -> plain numbers
md = re.sub(r'<a href="#([^"]+)" data-reference-type="ref" data-reference="[^"]+">[^<]*</a>',
            lambda m: labels.get(m.group(1), '?'), md)

# table captions "text {#tab:x}" -> "*Table N: text*"
def cap(m):
    lab = m.group(2)
    kind = 'Table' if lab.startswith('tab:') else 'Figure'
    return '*%s %s: %s*' % (kind, labels.get(lab, '?'), m.group(1).strip())
md = re.sub(r'^(.+?) \{#((?:tab|fig):[^}]+)\}$', cap, md, flags=re.M)

# bibliography
items = re.findall(r'\\bibitem\{[^}]*\}\s*(.*)', tex)
bib = ['# References', '']
for i, it in enumerate(items, 1):
    it = re.sub(r'\\emph\{([^}]*)\}', r'*\1*', it)
    it = re.sub(r'\\url\{([^}]*)\}', r'<\1>', it)
    it = it.replace('~', ' ').replace('\\ ', ' ')
    bib.append('%d. %s' % (i, it))
md = re.sub(r'<div class="thebibliography">.*?</div>\n', '\n'.join(bib) + '\n', md, flags=re.S)

# center divs
md = re.sub(r'^<div class="center">\n\n', '', md, flags=re.M)
md = re.sub(r'^</div>\n\n', '', md, flags=re.M)

# caps table footer
md = md.replace('*Table 1: Capability bits (`NodeCapabilities`).*',
    'Presets: `FORWARDER` = 17 (0x11), `CLIENT` = 54 (0x36), `BOTH` = 55 (0x37). '
    '`fromRole` upper-cases with `Locale.ROOT`; unknown or null roles map to 55.\n\n'
    '*Table 1: Capability bits (`NodeCapabilities`).*')

# handshake payload table (side by side in LaTeX)
hello = '''**`HELLO` payload (168 bytes)**

| Offset | Length | Field |
|---:|---:|:---|
| 0 | 32 | initiator Ed25519 pub |
| 32 | 32 | intended responder pub, or zeros |
| 64 | 32 | random challenge n_A |
| 96 | 8 | Unix time, seconds (i64) |
| 104 | 64 | signature |

**`HELLO_ACK` payload (192 bytes)**

| Offset | Length | Field |
|---:|---:|:---|
| 0 | 32 | responder Ed25519 pub |
| 32 | 32 | initiator pub (HELLO[0..32]) |
| 64 | 32 | SHA-256 of the full HELLO payload |
| 96 | 32 | random challenge n_B |
| 128 | 64 | signature |

*Table %s: Handshake payloads (`RudpAuth`).*
''' % labels['tab:hello']
md = re.sub(r'<table id="tab:hello">.*?</table>\n', hello, md, flags=re.S)

# figures -> text diagrams
arch = '''```
 +-------------------------------+   HTTP/JSON on 127.0.0.1    +-------------------------------+
 | FrostWire (desktop JVM)       | <-------------------------> | IceBridge daemon              |
 | UI, search performer,         |   X-IceBridge-Token         | icebridge.jar child process   |
 | LocalIndex, BTEngine, karma   |                             | rUDP, registry, relay         |
 +-------------------------------+                             +-------------------------------+
        ^  put/get identity, index, karma;                            ^  rUDP v2 (UDP),
        |  announce topics                                            |  signed packets
        v                                                             v
 +-------------------------------+   ip:rudpPort candidates    +-------------------------------+
 | BitTorrent Mainline DHT       | - - - - - - - - - - - - - > | Other IceBridge nodes         |
 | BEP 44 items, BEP 5 topics    |                             | desktops, phones, forwarders  |
 +-------------------------------+                             +-------------------------------+
        ^
        v
 +-------------------------------+
 | BitTorrent swarms             |
 | content transfer              |
 +-------------------------------+
```

*Figure %s: Processes and planes on a FrostWire desktop. On Android the IceBridge node runs in-process. A standalone forwarder runs the daemon alone, with its own DHT session.*
''' % labels['fig:arch']
hs = '''```
 Initiator A                                                     Responder B
     |  HELLO (cid, seq 0)  [pub_A, expected, n_A, t] sig_A          |
     |-------------------------------------------------------------->|
     |  HELLO_ACK  [pub_B, pub_A, SHA256(HELLO), n_B] sig_B          |
     |<--------------------------------------------------------------|
     |      both compute T = SHA256(HELLO payload || HELLO_ACK payload)
     |  HELLO_FINISH (empty, protected with T)                       |
     |-------------------------------------------------------------->|  B authenticated
     |  HELLO_READY (empty, protected with T)                        |
     |<--------------------------------------------------------------|
 A authenticated
```

*Figure %s: rUDP v2 handshake. "Protected" means signed as in Section %s.*
''' % (labels['fig:handshake'], labels['sec:protect'])
figs = re.findall(r'<figure id="(fig:[^"]+)".*?</figure>\n', md, flags=re.S)
md = re.sub(r'<figure id="fig:arch".*?</figure>\n', arch, md, flags=re.S)
md = re.sub(r'<figure id="fig:handshake".*?</figure>\n', hs, md, flags=re.S)

# limits longtable -> pipe table built from the tex source
lt = tex[tex.index('\\subsection{Limits and timers}'):]
lt = lt[lt.index('\\midrule\\endhead') + len('\\midrule\\endhead'):lt.index('\\bottomrule')]
rows = ['| Item | Value |', '|:---|:---|']
def tex2md(x):
    x = x.strip()
    x = re.sub(r'\\code\{([^}]*)\}', r'`\1`', x)
    x = x.replace('\\_', '_').replace('{,}', ',').replace('$\\pm$', '±')
    x = x.replace('$\\to$', '→').replace('$\\min(1024, 4096)$', 'min(1024, 4096)')
    x = x.replace('$500 \\cdot 2^{\\min(r,3)}$', '500 · 2^min(r,3)')
    x = x.replace('$-1$', '−1')
    return x
for line in lt.strip().split('\n'):
    line = line.strip()
    m = re.match(r'\\multicolumn\{2\}\{l\}\{\\emph\{(.*)\}\}\\\\', line)
    if m:
        rows.append('| **%s** | |' % m.group(1))
        continue
    if '&' in line:
        a, b = line.rstrip('\\').split('&', 1)
        rows.append('| %s | %s |' % (tex2md(a), tex2md(b.rstrip('\\ '))))
md = re.sub(r'(## Limits and timers\n\n)<table>.*?</table>\n', lambda m: m.group(1) + '\n'.join(rows) + '\n', md, flags=re.S)

# number headings like LaTeX (sections 1.., appendix A..)
out = []
sec = sub = subsub = 0
appendix = False
in_code = False
for line in md.split('\n'):
    if line.startswith('```'):
        in_code = not in_code
    if not in_code:
        m = re.match(r'^(#{1,3}) (.*)$', line)
        if m and m.group(2) != 'References':
            level = len(m.group(1))
            title = m.group(2)
            if title == 'Wire formats and constants':
                appendix = True
                sec = 0
            if level == 1:
                sec += 1; sub = subsub = 0
                num = chr(64 + sec) if appendix else str(sec)
                label = ('Appendix %s.' % num) if appendix else num
                line = '## %s %s' % (label, title)
            elif level == 2:
                sub += 1; subsub = 0
                num = (chr(64 + sec) if appendix else str(sec)) + '.' + str(sub)
                line = '### %s %s' % (num, title)
            else:
                subsub += 1
                line = '#### %s' % title
        elif line == '# References':
            line = '## References'
    out.append(line)
md = '\n'.join(out)

md = re.sub(r'^#### (.+?)\n\n', lambda m: '**' + m.group(1) + ('' if m.group(1).endswith(('.', ':')) else '.') + '** ', md, flags=re.M)
md = re.sub(r'^([A-Z][^\n|`]{2,60}[.)])  \n', r'**\1**  \n', md, flags=re.M)
# math: GitHub renders $`...`$ ; keep. Tidy stray HTML comments from pandoc.
md = md.replace('<!-- -->', '')

header = '''# IceBridge: an Authenticated Peer-to-Peer Service Mesh over UDP, with Rendezvous on the BitTorrent DHT

**Technical White Paper and Implementer's Specification**

Angel Leon — FrostWire · November 2026

> This Markdown file is generated from [`icebridge-whitepaper.tex`](icebridge-whitepaper.tex), which is the
> source of truth. The typeset version is [`icebridge-whitepaper.pdf`](icebridge-whitepaper.pdf). See
> [README.md](README.md) for how to rebuild both. Figures are drawn as text here; section, table and
> figure numbers match the PDF.

'''
abstract = re.search(r'\\begin\{abstract\}\s*\\noindent\s*(.*?)\\end\{abstract\}', tex, flags=re.S).group(1)
abstract = ' '.join(abstract.split())
abstract = abstract.replace('BEP~', 'BEP ').replace('``', '“').replace("''", '”').replace('version~2', 'version 2')
header += '## Abstract\n\n' + abstract + '\n\n'
open(out_path, 'w').write(header + md.lstrip())
print('wrote', out_path)
