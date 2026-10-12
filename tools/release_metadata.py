import datetime, hashlib, json, pathlib, re, sys, zipfile
root = pathlib.Path(__file__).resolve().parents[1]
bundle = pathlib.Path(sys.argv[1]).resolve()
version = re.search(r'^version\s*=\s*(.+)$', (root/'gradle.properties').read_text(), re.M)[1].strip()
assert bundle.name == f'patches-{version}.mpp'
with zipfile.ZipFile(bundle) as archive:
    assert archive.testzip() is None
    assert 'classes.dex' in archive.namelist(), 'Incomplete Android bundle'
    assert any('extensions' in p and p.endswith('.dex') for p in archive.namelist())
    for extension in ['youtube', 'shared-youtube', 'shared', 'music', 'reddit']:
        assert archive.getinfo(f'extensions/{extension}.mpe').file_size > 10000, 'Missing Android extension'
data = {'created_at':datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S'),
        'description':f'Moon Patches {version}. Nostr rooms and Moon branding. See CHANGELOG.md and docs/validation.md.',
        'download_url':f'https://github.com/noire342/moon-patches/releases/download/v{version}/{bundle.name}',
        'signature_download_url':'', 'version':version}
(root/'patches-bundle.json').write_text(json.dumps(data,indent=2)+'\n')
(bundle.parent/'SHA256SUMS.txt').write_text(hashlib.sha256(bundle.read_bytes()).hexdigest()+'  '+bundle.name+'\n')
print(f'Validated {bundle.name}; metadata and checksum written')
