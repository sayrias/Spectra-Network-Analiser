#!/usr/bin/env python3
"""Local documentation, SVG, license inclusion and product-version checks."""
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET

from project_bundle import source_files

root = Path(__file__).resolve().parents[1]
files = source_files(root)
checks = 0
for path in files:
    if path.suffix == '.md':
        text = path.read_text()
        # Ignore command examples: validate actual Markdown/HTML navigation only.
        text = re.sub(r'```.*?```', '', text, flags=re.S)
        refs = re.findall(r'\]\(([^\s)]+)\)', text)
        refs += re.findall(r'(?:href|src)="([^"]+)"', text)
        for ref in refs:
            parsed = urlsplit(ref)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            target = (path.parent / unquote(parsed.path)).resolve()
            assert target.is_relative_to(root), (path, ref, 'outside project')
            assert target.exists(), (path, ref, 'broken link')
            checks += 1
    elif path.suffix == '.svg':
        tree = ET.parse(path)
        assert tree.getroot().tag == '{http://www.w3.org/2000/svg}svg'
        assert not any(node.tag.endswith('script') for node in tree.iter())
        checks += 1

gradle = (root / 'android-app/app/build.gradle').read_text()
version = re.search(r"versionName '([^']+)'", gradle).group(1)
assert version == '1.1.0'
assert re.search(r'versionCode\s+11\b', gradle)
firmware = (root / 'firmware/src/main.cpp').read_text()
versions = re.findall(r'doc\["firmware"\] = "([^"]+)";', firmware)
assert len(versions) == 3 and set(versions) == {version}
assert f'SPECTRA {version} · özel arayüz' in (root / 'tools/ui_v35_smoke.py').read_text()
assert f'v{version}' in (root / 'README.md').read_text()
assert f'v{version}' in (root / 'docs/assets/hero.svg').read_text()
assert (root / 'LICENSE.md') in files
assert (root / 'docs/assets/hero.svg') in files
print(f'[OK] {checks} local documentation links/SVGs, license packaging and v{version} consistency')
