import os
import sys
import shutil
import subprocess
import threading
import time
import json
import logging
import config

log = logging.getLogger('ota')


class OTAUpdater:
    def __init__(self, notif=None):
        self._notif = notif
        self._repo_url = config.OTA_REPO_URL
        self._branch = config.OTA_BRANCH
        self._auto = config.OTA_AUTO_UPDATE
        self._enabled = config.OTA_ENABLED
        self._installed_version = self._read_version()
        self._available_version = ''
        self._check_timer: threading.Timer = None

        os.makedirs(config.OTA_PATH, exist_ok=True)

    def _read_version(self) -> str:
        vfile = os.path.join(config.BASE_DIR, 'version.json')
        if os.path.exists(vfile):
            try:
                with open(vfile) as f:
                    return json.load(f).get('version', 'unknown')
            except Exception:
                pass
        return 'unknown'

    def start(self):
        if not self._enabled or not self._repo_url:
            return
        log.info(f'OTA updater started (repo={self._repo_url}, branch={self._branch})')
        self._schedule_check()

    def _schedule_check(self):
        self._check_timer = threading.Timer(config.OTA_CHECK_INTERVAL, self._check_and_update)
        self._check_timer.daemon = True
        self._check_timer.start()

    def check_now(self) -> dict:
        return self._check_and_update()

    def _check_and_update(self) -> dict:
        try:
            log.info('Checking for updates...')
            result = subprocess.run(
                ['git', 'ls-remote', '--heads', self._repo_url, self._branch],
                capture_output=True, text=True, timeout=30, cwd=config.BASE_DIR
            )
            if result.returncode != 0:
                log.warning(f'OTA check failed: {result.stderr.strip()}')
                return {'success': False, 'error': result.stderr.strip()}

            remote_hash = result.stdout.split()[0] if result.stdout else ''
            if not remote_hash:
                return {'success': False, 'error': 'no hash'}

            self._available_version = remote_hash[:12]

            local_hash = self._get_local_hash()
            has_update = local_hash and remote_hash[:len(local_hash)] != local_hash

            if has_update:
                log.info(f'Update available: {self._installed_version} → {self._available_version}')
                if self._notif:
                    self._notif.send(
                        f'Update {self._available_version} available',
                        title='OTA Update'
                    )
                if self._auto:
                    return self._apply_update()
                return {'success': True, 'update_available': True,
                        'version': self._available_version}

            log.info('No updates available')
            return {'success': True, 'update_available': False}

        except Exception as e:
            log.warning(f'OTA check error: {e}')
            return {'success': False, 'error': str(e)}
        finally:
            self._schedule_check()

    def _get_local_hash(self) -> str:
        try:
            result = subprocess.run(
                ['git', 'rev-parse', 'HEAD'],
                capture_output=True, text=True, timeout=10, cwd=config.BASE_DIR
            )
            if result.returncode == 0:
                return result.stdout.strip()[:12]
        except Exception:
            pass
        return ''

    def _apply_update(self) -> dict:
        log.info('Applying OTA update...')
        backup_dir = f'{config.BASE_DIR}.bak'
        try:
            if os.path.exists(backup_dir):
                shutil.rmtree(backup_dir)
            shutil.copytree(config.BASE_DIR, backup_dir,
                            ignore=shutil.ignore_patterns('venv', '__pycache__', '*.pyc', '.git',
                                                          'logs', 'faces', 'clips', 'snapshots'))

            result = subprocess.run(
                ['git', 'pull', 'origin', self._branch],
                capture_output=True, text=True, timeout=120, cwd=config.BASE_DIR
            )
            if result.returncode != 0:
                log.error(f'OTA pull failed: {result.stderr}')
                shutil.rmtree(config.BASE_DIR)
                shutil.copytree(backup_dir, config.BASE_DIR)
                shutil.rmtree(backup_dir)
                return {'success': False, 'error': result.stderr.strip()}

            result = subprocess.run(
                [sys.executable, '-m', 'pip', 'install', '-r',
                 os.path.join(config.BASE_DIR, 'requirements.txt')],
                capture_output=True, text=True, timeout=300,
                cwd=config.BASE_DIR
            )
            if result.returncode != 0:
                log.warning(f'Pip install after update had issues: {result.stderr[:200]}')

            new_ver = self._read_version()
            log.info(f'OTA update applied: {self._installed_version} → {new_ver}')
            if self._notif:
                self._notif.send(f'Updated to {new_ver} — restarting', title='OTA Update')

            if os.path.exists(backup_dir):
                shutil.rmtree(backup_dir)

            return {'success': True, 'old_version': self._installed_version,
                    'new_version': new_ver}

        except Exception as e:
            log.error(f'OTA update failed: {e}')
            if os.path.exists(backup_dir):
                shutil.rmtree(config.BASE_DIR)
                shutil.move(backup_dir, config.BASE_DIR)
            return {'success': False, 'error': str(e)}

    def stop(self):
        if self._check_timer and self._check_timer.is_alive():
            self._check_timer.cancel()
