#!/usr/bin/env python3
"""Generates vdp_renderer_hw_vulkan.cpp from its template by copying the API-agnostic line ranges of
vdp_renderer_hw_d3d12.cpp referenced by //@@COPY a-b@@ markers. Run from libs/gbsat-core."""
import re, sys
base = 'src/ymir/hw/vdp/renderer/'
d3d = open(base + 'vdp_renderer_hw_d3d12.cpp').read().split('\n')
tpl = open(base + 'vulkan/vdp_renderer_hw_vulkan.cpp.in').read()
def rep(m):
    a, b = int(m.group(1)), int(m.group(2))
    return '// ---- begin copy of vdp_renderer_hw_d3d12.cpp lines %d-%d ----\n' % (a, b) + \
        '\n'.join(d3d[a - 1:b]) + '\n    // ---- end copy ----'
out = re.sub(r'^[ \t]*//@@COPY (\d+)-(\d+)@@', rep, tpl, flags=re.M)
bad = [l for l in out.split('\n') if re.search(r'D3D12|cmdList|barrierTracker|ID3D12', l) and not l.lstrip().startswith('//')]
if bad:
    print('D3D12 leftovers:\n' + '\n'.join(bad[:20])); sys.exit(1)
open(base + 'vdp_renderer_hw_vulkan.cpp', 'w').write(out)
print('ok', len(out.split('\n')), 'lines')
