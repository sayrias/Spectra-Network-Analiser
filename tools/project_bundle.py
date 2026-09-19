#!/usr/bin/env python3
"""Explicit source packaging and recoverable, project-local output cleanup."""
import argparse
import hashlib
import os
import re
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]
FILES = (
    '.gitignore', '.gitattributes', 'README.md', 'build.sh', 'THIRD_PARTY_NOTICES.md',
    'LICENSE', 'LICENSE.md', 'release/README.md',
    'firmware/.gitignore', 'firmware/platformio.ini', 'firmware/README.md',
    'android-app/.gitignore', 'android-app/README.md', 'android-app/build.gradle',
    'android-app/settings.gradle', 'android-app/gradle.properties',
    'android-app/gradlew', 'android-app/gradlew.bat',
    'android-app/gradle/wrapper/gradle-wrapper.jar',
    'android-app/gradle/wrapper/gradle-wrapper.properties',
    'android-app/app/build.gradle', 'android-app/app/proguard-rules.pro',
)
TREES = {
    'scripts': {'.sh'}, 'tools': {'.py', '.java', '.md'}, 'docs': {'.md'},
    'firmware/src': {'.cpp', '.h'}, 'firmware/docs': {'.md'},
    'android-app/app/src': {'.java', '.xml', '.ttf', '.txt'},
}
CACHES = (
    '.run', 'firmware/.pio', 'android-app/.gradle', 'android-app/build',
    'android-app/app/build', 'tools/__pycache__', 'release/screenshots',
)


def checked_path(root, relative):
    """Reject traversal and symlinks, including symlinked ancestor directories."""
    relative = Path(relative)
    if relative.is_absolute() or '..' in relative.parts or not relative.parts:
        raise ValueError(f'Güvensiz göreli yol: {relative}')
    current = root
    for part in relative.parts:
        current = current / part
        if current.is_symlink():
            raise ValueError(f'Sembolik bağlantı işlenmedi: {relative}')
    if not current.resolve().is_relative_to(root.resolve()):
        raise ValueError(f'Proje dışına çıkan yol: {relative}')
    return current


def source_files(root):
    paths = set()
    for relative in FILES:
        path = checked_path(root, relative)
        if path.is_file():
            paths.add(path)
    for directory, extensions in TREES.items():
        base = checked_path(root, directory)
        if not base.exists():
            continue
        for folder, subdirs, names in os.walk(base, followlinks=False):
            subdirs[:] = sorted(d for d in subdirs if not d.startswith('.') and d != '__pycache__')
            for directory_name in subdirs:
                checked_path(root, (Path(folder) / directory_name).relative_to(root))
            for name in names:
                if name.startswith('.') or Path(name).suffix not in extensions:
                    continue
                paths.add(checked_path(root, (Path(folder) / name).relative_to(root)))
    return sorted(paths)


def clean_targets(root):
    targets = [checked_path(root, p) for p in CACHES]
    output = checked_path(root, 'release')
    if output.exists():
        for path in output.iterdir():
            if path.suffix in {'.apk', '.bin'} or path.name in {
                'SPECTRA24-source.tar.gz', 'SPECTRA24-source.tar.gz.sha256',
            }:
                targets.append(checked_path(root, path.relative_to(root)))
    return [p for p in targets if p.exists()]


def package(root):
    sources = source_files(root)
    if not sources:
        raise ValueError('Kaynak dosyası bulunamadı.')
    # This is a basic guard, not a complete secret scanner. Review before publishing.
    for path in sources:
        if path.suffix not in {'.ttf', '.jar'}:
            data = path.read_bytes()
            if re.search(rb'^-----BEGIN (?:[A-Z0-9]+ )?PRIVATE KEY-----', data, re.MULTILINE):
                raise ValueError(f'Özel anahtar belirtisi: {path.relative_to(root)}')
    output = checked_path(root, 'release')
    output.mkdir(exist_ok=True)
    archive = checked_path(root, 'release/SPECTRA24-source.tar.gz')
    checksum = checked_path(root, 'release/SPECTRA24-source.tar.gz.sha256')
    with tempfile.TemporaryDirectory(prefix='spectra-source-') as staging:
        temporary = Path(staging) / archive.name
        with tarfile.open(temporary, 'w:gz') as bundle:
            for path in sources:
                info = bundle.gettarinfo(str(path), 'SPECTRA24/' + path.relative_to(root).as_posix())
                info.uid = info.gid = 0
                info.uname = info.gname = ''
                info.mtime = 0
                info.mode = 0o755 if path.suffix == '.sh' or path.name == 'gradlew' else 0o644
                with path.open('rb') as stream:
                    bundle.addfile(info, stream)
        shutil.copyfile(temporary, archive)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    checksum.write_text(f'{digest}  {archive.name}\n', encoding='utf-8')
    print(f'[OK] {len(sources)} kaynak dosyası: {archive}')
    print(f'[OK] SHA-256: {checksum}')
    print('SDK, local.properties, APK/BIN, kayıtlar ve önbellekler pakete dahil edilmedi.')
    print('Yayımlamadan önce kaynak lisansını seçin ve dosyaları gözden geçirin.')


def clean(root, dry_run=False, yes=False):
    targets = clean_targets(root)
    if not targets:
        print('Temizlenecek derleme çıktısı yok.')
        return
    print('Çöp kutusuna taşınacak proje çıktıları:')
    for path in targets:
        print(f'  {path.relative_to(root)}')
    if dry_run:
        return
    # Keep process ownership information until the dedicated stop command runs.
    run_dir = checked_path(root, '.run')
    if run_dir.exists() and any(run_dir.glob('*.pid')):
        raise ValueError('Önce ./build.sh stop çalıştırın; test PID kayıtları var.')
    if not yes:
        try:
            answer = input('Geri alınabilir şekilde temizlensin mi? [e/H]: ')
        except EOFError:
            answer = ''
        if answer.lower() != 'e':
            print('Temizlik iptal edildi.')
            return
    gio = shutil.which('gio')
    if not gio:
        raise ValueError('Geri alınabilir temizlik için gio gerekli. Kalıcı silme yapılmadı.')
    subprocess.run([gio, 'trash', '--', *(str(p) for p in targets)], check=True)
    print('[OK] Çöp kutusuna taşındı; dosya yöneticisinden geri yüklenebilir.')
    print('Kaynaklara, SDK / kullanıcı genel önbelleklerine ve local.properties dosyasına dokunulmadı.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('package')
    cleanup = commands.add_parser('clean')
    flags = cleanup.add_mutually_exclusive_group()
    flags.add_argument('--dry-run', action='store_true')
    flags.add_argument('--yes', action='store_true')
    args = parser.parse_args()
    try:
        if args.command == 'package':
            package(ROOT)
        else:
            clean(ROOT, args.dry_run, args.yes)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'HATA: {error}\n')


if __name__ == '__main__':
    main()
