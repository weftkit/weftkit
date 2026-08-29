"""Post-processes the generated sitemap: pages marked noindex are removed, keeping
it consistent with their robots meta tag, and lastmod becomes the source file's git
commit date instead of the build date. Also exposes that date to page templates as
page.meta.iso_mdate for schema.org dateModified and the visible last-updated line."""

import gzip
import re
import subprocess
from pathlib import Path

noindexed = set()
git_dates = {}
_history_ok = None


def _git(args, cwd):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=True).stdout.strip()


def _full_history(cwd):
    global _history_ok
    if _history_ok is None:
        try:
            if _git(["rev-parse", "--is-shallow-repository"], cwd) == "true":
                subprocess.run(["git", "fetch", "--quiet", "--unshallow"], cwd=cwd, capture_output=True)
            _history_ok = _git(["rev-parse", "--is-shallow-repository"], cwd) == "false"
        except (OSError, subprocess.CalledProcessError):
            _history_ok = False
    return _history_ok


def _git_date(path, cwd):
    if not _full_history(cwd):
        return None
    try:
        return _git(["log", "-1", "--format=%cI", "--", path], cwd) or None
    except (OSError, subprocess.CalledProcessError):
        return None


def on_page_context(context, page, config, nav):
    url = config["site_url"].rstrip("/") + "/" + page.url
    if "noindex" in page.meta.get("robots", ""):
        noindexed.add(url)
    date = _git_date(page.file.abs_src_path, config["docs_dir"])
    if date:
        page.meta["iso_mdate"] = date
        git_dates[url] = date[:10]
    return context


def on_post_build(config):
    sitemap = Path(config["site_dir"]) / "sitemap.xml"
    if not sitemap.exists():
        return
    content = sitemap.read_text(encoding="utf-8")
    for url in noindexed:
        content = re.sub(rf"\s*<url>\s*<loc>{re.escape(url)}</loc>.*?</url>", "", content, flags=re.S)

    def lastmod(match):
        date = git_dates.get(match.group(1))
        if date is None:
            return match.group(0)
        return f"<loc>{match.group(1)}</loc>\n         <lastmod>{date}</lastmod>"

    content = re.sub(r"<loc>([^<]+)</loc>\s*<lastmod>[^<]*</lastmod>", lastmod, content)
    sitemap.write_text(content, encoding="utf-8")
    gz = sitemap.with_suffix(".xml.gz")
    if gz.exists():
        gz.write_bytes(gzip.compress(content.encode("utf-8")))
