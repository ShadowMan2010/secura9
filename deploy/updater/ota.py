"""
SECURA-9 OTA Updater.
Checks for updates, applies them atomically with rollback on failure.
"""
import json
import logging
import os
import shutil
import subprocess
import sys
import time
import tempfile

log = logging.getLogger('ota')

SECURA9_DIR = '/opt/secura9'
REPO_URL = os.environ.get('SECURA9_REPO_URL', '')
UPDATE_BRANCH = os.environ.get('SECURA9_UPDATE_BRANCH', 'main')
CONFIG_FILE = '/etc/secura9/ota.conf'
VERSION_FILE = os.path.join(SECURA9_DIR, 'version.json')
BACKUP_DIR = '/opt/secura9-backup'


def current_version():
    try:
        with open(VERSION_FILE) as f:
            return json.load(f).get('version', '0.0.0')
    except Exception:
        return '0.0.0'


def remote_version():
    if not REPO_URL:
        return None
    try:
        # Use git ls-remote to check latest commit without cloning
        r = subprocess.run(
            ['git', 'ls-remote', REPO_URL, f'refs/heads/{UPDATE_BRANCH}'],
            capture_output=True, text=True, timeout=15,
        )
        if r.returncode == 0 and r.stdout.strip():
            sha = r.stdout.strip().split()[0]
            return sha[:8]
    except Exception as e:
        log.warning(f'Failed to check remote version: {e}')
    return None


def check_update():
    """Return (has_update, current_ver, remote_ver) or (False, cur, None)."""
    cur = current_version()
    remote = remote_version()
    if not remote:
        return False, cur, None
    has = remote != cur
    return has, cur, remote


def apply_update():
    """Pull latest code, install deps, swap. Returns True on success."""
    tmp = tempfile.mkdtemp(prefix='secura9-update-')
    try:
        log.info('Cloning latest code...')
        r = subprocess.run(
            ['git', 'clone', '--depth=1', '-b', UPDATE_BRANCH, REPO_URL, tmp],
            capture_output=True, text=True, timeout=120,
        )
        if r.returncode != 0:
            log.error(f'Clone failed: {r.stderr}')
            return False

        log.info('Installing Python dependencies...')
        venv_pip = os.path.join(SECURA9_DIR, 'venv', 'bin', 'pip')
        req_file = os.path.join(tmp, 'requirements.txt')
        if os.path.exists(req_file):
            r = subprocess.run(
                [venv_pip, 'install', '-r', req_file],
                capture_output=True, text=True, timeout=300,
            )
            if r.returncode != 0:
                log.error(f'pip install failed: {r.stderr}')
                return False

        # Backup current
        if os.path.exists(BACKUP_DIR):
            shutil.rmtree(BACKUP_DIR)
        if os.path.exists(SECURA9_DIR):
            shutil.copytree(SECURA9_DIR, BACKUP_DIR, symlinks=True,
                            ignore=shutil.ignore_patterns('venv', 'env', '__pycache__', '*.pyc', 'faces', 'clips', 'logs', 'snapshots'))

        # Swap code (keep venv, data dirs)
        for item in os.listdir(tmp):
            src = os.path.join(tmp, item)
            dst = os.path.join(SECURA9_DIR, item)
            if item in ('venv', 'env', 'faces', 'clips', 'logs', 'snapshots', '__pycache__'):
                continue
            if os.path.exists(dst):
                if os.path.isdir(dst):
                    shutil.rmtree(dst)
                else:
                    os.remove(dst)
            shutil.move(src, dst)

        # Write version
        new_ver = remote_version() or 'unknown'
        with open(VERSION_FILE, 'w') as f:
            json.dump({'version': new_ver, 'updated_at': time.time()}, f)

        log.info(f'Update to {new_ver} applied successfully')
        return True

    except Exception as e:
        log.error(f'Update failed: {e}')
        # Rollback
        if os.path.exists(BACKUP_DIR):
            log.info('Rolling back...')
            for item in os.listdir(BACKUP_DIR):
                src = os.path.join(BACKUP_DIR, item)
                dst = os.path.join(SECURA9_DIR, item)
                if item in ('venv', 'env'):
                    continue
                if os.path.exists(dst):
                    if os.path.isdir(dst):
                        shutil.rmtree(dst)
                    else:
                        os.remove(dst)
                shutil.move(src, dst)
            log.info('Rollback complete')
        return False
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] ota: %(message)s')

    if not REPO_URL:
        log.info('No REPO_URL set — update check skipped')
        return

    has_update, cur, remote = check_update()
    if not has_update:
        log.info(f'Already up-to-date ({cur})')
        return

    log.info(f'Update available: {cur} -> {remote}')
    ok = apply_update()
    if ok:
        log.info('Update applied. Restarting service...')
        subprocess.run(['systemctl', 'restart', 'secura9'], check=False)
    else:
        log.error('Update failed')


if __name__ == '__main__':
    main()
