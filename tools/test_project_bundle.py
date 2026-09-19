#!/usr/bin/env python3
"""No device or trash access: packaging/cleanup policy regression tests."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import tarfile
import hashlib
from project_bundle import source_files, clean_targets, checked_path, package, clean


class BundleTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='spectra-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write(self, name, text='test'):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def test_sources_exclude_private_and_generated(self):
        self.write('build.sh', '#!/bin/bash\n')
        self.write('android-app/app/src/main/Main.java')
        self.write('android-app/gradle/wrapper/gradle-wrapper.jar')
        for name in ('android-app/local.properties', '.env', 'scripts/.env',
                     'android-app/app/build/generated/Foo.java', 'release/test.apk',
                     'android-app/app/src/main/secret.jks', '.run/user.log'):
            self.write(name)
        paths = {p.relative_to(self.root).as_posix() for p in source_files(self.root)}
        self.assertEqual(paths, {'build.sh', 'android-app/app/src/main/Main.java',
                                 'android-app/gradle/wrapper/gradle-wrapper.jar'})

    def test_cleanup_preserves_sources_and_sdk_settings(self):
        self.write('release/README.md')
        self.write('android-app/local.properties')
        self.write('firmware/src/main.cpp')
        self.write('release/test.apk')
        self.write('release/SPECTRA24-source.tar.gz')
        self.write('android-app/app/build/test.class')
        targets = {p.relative_to(self.root).as_posix() for p in clean_targets(self.root)}
        self.assertEqual(targets, {'release/test.apk', 'release/SPECTRA24-source.tar.gz',
                                   'android-app/app/build'})

    def test_reject_unsafe_targets(self):
        for name in ('../other', '/tmp', '.'):
            with self.subTest(name=name), self.assertRaises(ValueError):
                checked_path(self.root, name)
        (self.root / 'release').symlink_to('/tmp', target_is_directory=True)
        with self.assertRaises(ValueError):
            clean_targets(self.root)

    def test_reject_source_symlink(self):
        (self.root / 'scripts').symlink_to('/tmp', target_is_directory=True)
        with self.assertRaises(ValueError):
            source_files(self.root)

    def test_archive_content_modes_hash(self):
        self.write('README.md')
        self.write('build.sh')
        self.write('android-app/local.properties')
        package(self.root)
        archive = self.root / 'release/SPECTRA24-source.tar.gz'
        with tarfile.open(archive) as bundle:
            self.assertEqual(set(bundle.getnames()), {'SPECTRA24/README.md', 'SPECTRA24/build.sh'})
            self.assertEqual(bundle.getmember('SPECTRA24/build.sh').mode, 0o755)
            self.assertTrue(all(m.uid == 0 and m.uname == '' for m in bundle.getmembers()))
        checksum = Path(str(archive) + '.sha256').read_text().split()[0]
        self.assertEqual(checksum, hashlib.sha256(archive.read_bytes()).hexdigest())

    def test_private_key_guard(self):
        self.write('docs/key.md', '-----BEGIN RSA PRIVATE KEY-----\n')
        with self.assertRaises(ValueError):
            package(self.root)

    def test_clean_dry_run_does_not_delete(self):
        file = self.write('release/test.apk')
        with patch('project_bundle.subprocess.run') as run:
            clean(self.root, dry_run=True)
            run.assert_not_called()
        self.assertTrue(file.exists())

    def test_clean_refuses_pid_files(self):
        self.write('.run/simulator.pid', '999999')
        with self.assertRaises(ValueError):
            clean(self.root, yes=True)


if __name__ == '__main__':
    unittest.main()
