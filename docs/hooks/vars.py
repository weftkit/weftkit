import re
from pathlib import Path

import yaml

_LINK = re.compile(r"\[([^\]]+)\]\([^)]+\)")
_EMPHASIS = re.compile(r"\*{1,2}([^*]+)\*{1,2}")


def _plain(md):
    text = _LINK.sub(r"\1", md)
    text = _EMPHASIS.sub(r"\1", text)
    return " ".join(text.split())


def on_config(config):
    path = Path(config["config_file_path"]).parent / "data" / "faq.yml"
    faq = yaml.safe_load(path.read_text(encoding="utf-8"))
    for entry in faq:
        entry["a"] = " ".join(entry["a"].split())
        entry["schema"] = _plain(entry["a"])
    config["extra"]["faq"] = faq
    return config


def _render_faq(faq):
    return "\n\n".join(f'??? question "{e["q"]}"\n\n    {e["a"]}' for e in faq)


def on_page_markdown(markdown, page, config, files):
    markdown = markdown.replace("{{ weftkit_version }}", str(config["extra"]["weftkit_version"]))
    if "<!-- faq -->" in markdown:
        markdown = markdown.replace("<!-- faq -->", _render_faq(config["extra"]["faq"]))
    return markdown
