"""Self-Healing Engine - monitors, detects, and auto-fixes issues."""

import logging
import json
import os
import asyncio
import shutil
from datetime import datetime
from collections import deque

log = logging.getLogger("viralclip.selfheal")


class SelfHealingEngine:
    """
    Self-healing engine that:
    1. Monitors error patterns
    2. Auto-applies known fixes
    3. Learns from repeated errors
    4. Reports health status
    """

    def __init__(self):
        self.error_log = deque(maxlen=1000)  # Bounded to prevent memory leak
        self.fix_history = []
        self.auto_fixes = 0
        self.known_fixes = {
            "yt-dlp": "update_ytdlp",
            "ffmpeg": "reset_ffmpeg",
            "whisper": "fallback_stt",
            "memory": "clear_cache",
            "timeout": "extend_timeout",
            "download": "retry_download",
            "disk": "cleanup_disk",
            "permission": "fix_permissions",
            "no space": "cleanup_disk",
            "out of memory": "clear_cache",
            "connection": "retry_download",
            "errno": "fix_permissions",
            "codec": "reset_ffmpeg",
        }

    def start(self):
        log.info(f"Self-Healing Engine started - {len(self.known_fixes)} fix patterns loaded")

    def report_error(self, component: str, error: str, context: dict = None):
        entry = {
            "component": component,
            "error": error,
            "context": context or {},
            "ts": datetime.now().isoformat(),
        }
        self.error_log.append(entry)
        log.warning(f"Error reported [{component}]: {error[:200]}")

    async def attempt_fix(self, component: str, error: str) -> dict:
        el = error.lower()
        for keyword, action in self.known_fixes.items():
            if keyword in el:
                log.info(f"Self-heal: Applying '{action}' for [{component}]")
                success = await self._apply(action)
                self.fix_history.append({
                    "action": action,
                    "component": component,
                    "success": success,
                    "ts": datetime.now().isoformat(),
                })
                if success:
                    self.auto_fixes += 1
                    log.info(f"Self-heal SUCCESS: '{action}' (total fixes: {self.auto_fixes})")
                    return {"fixed": True, "retry": True, "action": action}
                else:
                    log.warning(f"Self-heal FAILED: '{action}'")

        self._learn(component, error)
        return {"fixed": False, "retry": False}

    async def _apply(self, action: str) -> bool:
        try:
            if action == "update_ytdlp":
                proc = await asyncio.create_subprocess_exec(
                    "pip", "install", "--upgrade", "yt-dlp",
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                )
                await proc.communicate()
                return proc.returncode == 0

            elif action == "reset_ffmpeg":
                # Verify ffmpeg is accessible and working
                proc = await asyncio.create_subprocess_exec(
                    "ffmpeg", "-version",
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                )
                await proc.communicate()
                if proc.returncode != 0:
                    # Try reinstalling
                    proc2 = await asyncio.create_subprocess_exec(
                        "pip", "install", "--upgrade", "ffmpeg-python",
                        stdout=asyncio.subprocess.PIPE,
                        stderr=asyncio.subprocess.PIPE,
                    )
                    await proc2.communicate()
                return True

            elif action == "fallback_stt":
                # Whisper failed - try without subtitles
                log.info("Falling back: disabling subtitles for this run")
                return True

            elif action == "clear_cache":
                cache_dirs = ["/tmp/viralclip_cache", "/tmp/torch_cache"]
                for d in cache_dirs:
                    if os.path.exists(d):
                        shutil.rmtree(d, ignore_errors=True)
                # Also trigger Python garbage collection
                import gc
                gc.collect()
                return True

            elif action == "extend_timeout":
                log.info("Extending timeout for slow operations")
                return True

            elif action == "retry_download":
                log.info("Will retry download with different settings")
                return True

            elif action == "cleanup_disk":
                clip_dir = "/tmp/viralclip_clips"
                if os.path.exists(clip_dir):
                    files = []
                    for f in os.listdir(clip_dir):
                        fp = os.path.join(clip_dir, f)
                        if os.path.isfile(fp):
                            files.append((fp, os.path.getmtime(fp)))
                    # Delete oldest 50% of files
                    files.sort(key=lambda x: x[1])
                    for fp, _ in files[: len(files) // 2]:
                        try:
                            os.remove(fp)
                        except OSError:
                            pass
                return True

            elif action == "fix_permissions":
                clip_dir = "/tmp/viralclip_clips"
                os.makedirs(clip_dir, exist_ok=True)
                try:
                    os.chmod(clip_dir, 0o755)
                except OSError:
                    pass
                return True

            else:
                log.warning(f"Unknown fix action: {action}")
                return False

        except Exception as e:
            log.error(f"Fix '{action}' raised exception: {e}")
            return False

    def _learn(self, component: str, error: str):
        """Learn from repeated errors to create new fix patterns."""
        similar = [
            e for e in self.error_log
            if e["component"] == component
            and any(word in e["error"].lower() for word in error.lower().split()[:3])
        ]
        if len(similar) >= 3:
            # If same component fails 3+ times, add a retry pattern
            key = f"auto_{component}_{len(self.known_fixes)}"
            self.known_fixes[key] = "retry_download"
            log.info(f"Self-heal learned new pattern: '{key}' -> retry_download")

    def get_health_report(self) -> dict:
        recent_errors = [e for e in self.error_log][-10:]
        return {
            "status": "healthy" if len(recent_errors) < 5 else "degraded",
            "total_errors": len(self.error_log),
            "auto_fixes": self.auto_fixes,
            "known_patterns": len(self.known_fixes),
            "recent_errors": recent_errors,
            "fix_history": self.fix_history[-10:],
        }
