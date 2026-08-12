"""Removes pages marked noindex from the generated sitemap, keeping it consistent
with their robots meta tag."""

import gzip
import re
from pathlib import Path

noindexed = set()


def on_page_context(context, page, config, nav):
    robots = page.meta.get("robots", "")
    if "noindex" in robots:
        noindexed.add(config["site_url"].rstrip("/") + "/" + page.url)
    return context


def on_post_build(config):
    sitemap = Path(config["site_dir"]) / "sitemap.xml"
    if not sitemap.exists() or not noindexed:
        return
    content = sitemap.read_text(encoding="utf-8")
    for url in noindexed:
        content = re.sub(rf"\s*<url>\s*<loc>{re.escape(url)}</loc>.*?</url>", "", content, flags=re.S)
    sitemap.write_text(content, encoding="utf-8")
    gz = sitemap.with_suffix(".xml.gz")
    if gz.exists():
        gz.write_bytes(gzip.compress(content.encode("utf-8")))
