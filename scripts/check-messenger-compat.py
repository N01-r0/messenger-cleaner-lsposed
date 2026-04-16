#!/usr/bin/env python3
import argparse
import html
import json
import re
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path


MENU_LABELS = [
    "Chat with AIs",
    "Create an AI",
    "Facebook Reels",
    "Facebook Events",
    "Chat moments",
    "Meta AI",
]

AI_KILL_SWITCH_TOKENS = [
    "SearchAiagentImplementationsKillSwitch",
    "AiAgentPluginsKillSwitch",
]

INBOX_AD_ITEM_TOKEN = "Lcom/facebook/messaging/business/inboxads/common/InboxAdsItem;"
INBOX_METHOD_SIGNATURE = re.compile(
    r"\.method .*?\(([^)]*)\)Lcom/google/common/collect/ImmutableList;"
)


def run(cmd):
    return subprocess.run(cmd, check=True, text=True, capture_output=True)


def root_dir() -> Path:
    return Path(__file__).resolve().parent.parent


def default_report_dir() -> Path:
    return root_dir() / "reports"


def timestamp_slug() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%SZ")


def ensure_decoded(input_path: Path) -> tuple[Path, tempfile.TemporaryDirectory | None]:
    if input_path.is_dir():
        return input_path, None

    temp_dir = tempfile.TemporaryDirectory(prefix="messenger-compat-")
    decoded_dir = Path(temp_dir.name) / "decoded"
    subprocess.run(
        ["apktool", "d", "-f", str(input_path), "-o", str(decoded_dir)],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return decoded_dir, temp_dir


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def find_smali_files(decoded_dir: Path):
    return decoded_dir.rglob("*.smali")


def count_inbox_processor_candidates(decoded_dir: Path) -> int:
    count = 0
    for path in find_smali_files(decoded_dir):
        text = read_text(path)
        if INBOX_AD_ITEM_TOKEN not in text:
            continue
        if "Lcom/google/common/collect/ImmutableList;" not in text:
            continue
        if "Ljava/lang/String;" not in text:
            continue
        if "instance-of" not in text and "check-cast" not in text:
            continue
        if "InboxAdsItem" not in text:
            continue
        if INBOX_METHOD_SIGNATURE.search(text):
            count += 1
    return count


def collect_hits(decoded_dir: Path, needles: list[str]) -> dict[str, int]:
    hits = {needle: 0 for needle in needles}
    for path in decoded_dir.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix not in {".smali", ".xml", ".txt"}:
            continue
        text = read_text(path)
        for needle in needles:
            if needle in text:
                hits[needle] += 1
    return hits


def read_badging(apk_path: Path) -> dict[str, str]:
    try:
        output = run(["aapt", "dump", "badging", str(apk_path)]).stdout
    except Exception:
        return {}

    result = {}
    package_line = next((line for line in output.splitlines() if line.startswith("package: ")), "")
    if package_line:
        for key in ("name", "versionCode", "versionName"):
            match = re.search(rf"{key}='([^']+)'", package_line)
            if match:
                result[key] = match.group(1)
    return result


def pull_device_base_apk(package_name: str) -> tuple[Path, tempfile.TemporaryDirectory]:
    temp_dir = tempfile.TemporaryDirectory(prefix="messenger-device-apk-")
    temp_path = Path(temp_dir.name)

    pm_path_output = run(["adb", "shell", "pm", "path", package_name]).stdout.splitlines()
    base_paths = [line.removeprefix("package:") for line in pm_path_output if line.endswith("/base.apk")]
    if not base_paths:
        temp_dir.cleanup()
        raise SystemExit(f"Could not find base.apk for {package_name} on the attached device.")

    remote_base = base_paths[0]
    local_base = temp_path / "base.apk"
    subprocess.run(
        ["adb", "pull", remote_base, str(local_base)],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return local_base, temp_dir


def build_report(
    input_path: Path,
    decoded_dir: Path,
    *,
    source_type: str,
    source_label: str,
) -> dict:
    badging = read_badging(input_path) if input_path.is_file() else {}
    ai_hits = collect_hits(decoded_dir, AI_KILL_SWITCH_TOKENS)
    menu_hits = collect_hits(decoded_dir, MENU_LABELS)
    inbox_candidates = count_inbox_processor_candidates(decoded_dir)

    checks = [
        {
            "id": "ai_kill_switch_tokens",
            "status": "pass" if any(ai_hits.values()) else "fail",
            "details": ai_hits,
            "summary": "Meta AI kill-switch tokens were found." if any(ai_hits.values()) else "Meta AI kill-switch tokens were not found.",
        },
        {
            "id": "inbox_processor_candidates",
            "status": "pass" if inbox_candidates > 0 else "fail",
            "details": {"count": inbox_candidates},
            "summary": f"Found {inbox_candidates} inbox-ad processor candidate(s)." if inbox_candidates > 0 else "No inbox-ad processor candidates found.",
        },
        {
            "id": "menu_surface_strings",
            "status": "info",
            "details": menu_hits,
            "summary": "UI/menu strings are informational only; zero hits does not automatically mean failure.",
        },
    ]

    problems = [check["id"] for check in checks if check["status"] == "fail"]
    risk = "low"
    if problems:
        risk = "high"
    elif sum(menu_hits.values()) < 3:
        risk = "medium"

    recommendation = {
        "low": "Looks structurally compatible. Still do a short in-app smoke test after updating Messenger.",
        "medium": "Core hooks look plausible, but UI/menu surfaces may have shifted. Update only if you can smoke-test immediately.",
        "high": "Do not trust this Messenger build yet. The hook signatures need review before use.",
    }[risk]

    return {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "source": {
            "type": source_type,
            "label": source_label,
            "input_path": str(input_path),
            "decoded_path": str(decoded_dir),
        },
        "app": {
            "package": badging.get("name", "com.facebook.orca"),
            "version_name": badging.get("versionName", "unknown"),
            "version_code": badging.get("versionCode", "unknown"),
        },
        "risk": risk,
        "recommendation": recommendation,
        "checks": checks,
        "problems": problems,
    }


def render_markdown(report: dict) -> str:
    lines = [
        "# Messenger Precheck",
        "",
        f"- Generated: `{report['generated_at_utc']}`",
        f"- Source: `{report['source']['type']}`",
        f"- Label: `{report['source']['label']}`",
        f"- Package: `{report['app']['package']}`",
        f"- Version name: `{report['app']['version_name']}`",
        f"- Version code: `{report['app']['version_code']}`",
        f"- Risk: `{report['risk']}`",
        "",
        f"Recommendation: {report['recommendation']}",
        "",
        "## Checks",
    ]

    for check in report["checks"]:
        lines.append(f"- `{check['id']}`: `{check['status']}`")
        lines.append(f"  {check['summary']}")
        details = check["details"]
        if isinstance(details, dict):
            for key, value in details.items():
                lines.append(f"  `{key}`: `{value}`")

    if report["problems"]:
        lines.extend(["", "## Problems"])
        for problem in report["problems"]:
            lines.append(f"- `{problem}`")

    return "\n".join(lines) + "\n"


def render_html(report: dict) -> str:
    rows = []
    for check in report["checks"]:
        details = "<br>".join(
            f"<code>{html.escape(str(key))}</code>: <code>{html.escape(str(value))}</code>"
            for key, value in check["details"].items()
        )
        rows.append(
            "<tr>"
            f"<td><code>{html.escape(check['id'])}</code></td>"
            f"<td><code>{html.escape(check['status'])}</code></td>"
            f"<td>{html.escape(check['summary'])}</td>"
            f"<td>{details}</td>"
            "</tr>"
        )

    problems = "".join(f"<li><code>{html.escape(problem)}</code></li>" for problem in report["problems"])
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Messenger Precheck</title>
  <style>
    body {{ font-family: sans-serif; margin: 2rem; background: #111; color: #eee; }}
    code {{ background: #222; padding: 0.1rem 0.3rem; border-radius: 4px; }}
    table {{ border-collapse: collapse; width: 100%; margin-top: 1rem; }}
    th, td {{ border: 1px solid #333; padding: 0.6rem; vertical-align: top; }}
    th {{ background: #1d1d1d; text-align: left; }}
    .risk-low {{ color: #7ee787; }}
    .risk-medium {{ color: #f2cc60; }}
    .risk-high {{ color: #ff7b72; }}
  </style>
</head>
<body>
  <h1>Messenger Precheck</h1>
  <p><strong>Generated:</strong> <code>{html.escape(report['generated_at_utc'])}</code></p>
  <p><strong>Source:</strong> <code>{html.escape(report['source']['type'])}</code> <code>{html.escape(report['source']['label'])}</code></p>
  <p><strong>Package:</strong> <code>{html.escape(report['app']['package'])}</code></p>
  <p><strong>Version:</strong> <code>{html.escape(report['app']['version_name'])}</code> (<code>{html.escape(report['app']['version_code'])}</code>)</p>
  <p><strong>Risk:</strong> <span class="risk-{html.escape(report['risk'])}"><code>{html.escape(report['risk'])}</code></span></p>
  <p><strong>Recommendation:</strong> {html.escape(report['recommendation'])}</p>
  <table>
    <thead>
      <tr><th>Check</th><th>Status</th><th>Summary</th><th>Details</th></tr>
    </thead>
    <tbody>
      {''.join(rows)}
    </tbody>
  </table>
  {"<h2>Problems</h2><ul>" + problems + "</ul>" if problems else ""}
</body>
</html>
"""


def write_report_files(report: dict, report_dir: Path) -> dict[str, Path]:
    report_dir.mkdir(parents=True, exist_ok=True)
    stamp = timestamp_slug()
    stem = f"precheck-{stamp}"

    json_path = report_dir / f"{stem}.json"
    md_path = report_dir / f"{stem}.md"
    html_path = report_dir / f"{stem}.html"

    json_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    md_path.write_text(render_markdown(report), encoding="utf-8")
    html_path.write_text(render_html(report), encoding="utf-8")

    latest_json = report_dir / "latest.json"
    latest_md = report_dir / "latest.md"
    latest_html = report_dir / "latest.html"
    latest_json.write_text(json_path.read_text(encoding="utf-8"), encoding="utf-8")
    latest_md.write_text(md_path.read_text(encoding="utf-8"), encoding="utf-8")
    latest_html.write_text(html_path.read_text(encoding="utf-8"), encoding="utf-8")

    return {
        "json": json_path,
        "markdown": md_path,
        "html": html_path,
        "latest_json": latest_json,
        "latest_markdown": latest_md,
        "latest_html": latest_html,
    }


def print_console_report(report: dict, paths: dict[str, Path] | None):
    print("Messenger compatibility report")
    print(
        f"Package={report['app']['package']} "
        f"versionName={report['app']['version_name']} "
        f"versionCode={report['app']['version_code']}"
    )
    print(f"Source={report['source']['type']} ({report['source']['label']})")
    print(f"Decoded={report['source']['decoded_path']}")
    print(f"Risk={report['risk']}")
    print()
    print("Checks:")
    for check in report["checks"]:
        print(f"  {check['id']}: {check['status']} - {check['summary']}")
    if report["problems"]:
        print()
        print("Problems:")
        for problem in report["problems"]:
            print(f"  {problem}")
    print()
    print(f"Recommendation: {report['recommendation']}")
    if paths:
        print()
        print("Report files:")
        print(f"  Markdown: {paths['markdown']}")
        print(f"  HTML:     {paths['html']}")
        print(f"  JSON:     {paths['json']}")
        print(f"  Latest MD: {paths['latest_markdown']}")


def main():
    parser = argparse.ArgumentParser(description="Check Messenger APK compatibility with MessengerCleaner hooks.")
    parser.add_argument("input", nargs="?", help="Path to Messenger base.apk or an apktool-decoded directory")
    parser.add_argument("--device", action="store_true", help="Pull the currently installed Messenger base.apk from the attached device")
    parser.add_argument("--package", default="com.facebook.orca", help="Package name to pull when using --device")
    parser.add_argument("--report-dir", default=str(default_report_dir()), help="Directory to write Markdown/HTML/JSON reports to")
    parser.add_argument("--no-report", action="store_true", help="Skip writing report files and print only to stdout")
    args = parser.parse_args()

    if args.device == bool(args.input):
        raise SystemExit("Use exactly one source: either pass <input> or use --device.")

    source_temp_dir = None
    source_type = "apk"
    source_label = ""
    if args.device:
        input_path, source_temp_dir = pull_device_base_apk(args.package)
        source_type = "device"
        source_label = args.package
    else:
        input_path = Path(args.input).expanduser().resolve()
        source_type = "decoded" if input_path.is_dir() else "apk"
        source_label = input_path.name

    decoded_dir, decode_temp_dir = ensure_decoded(input_path)

    try:
        report = build_report(
            input_path,
            decoded_dir,
            source_type=source_type,
            source_label=source_label,
        )
        paths = None if args.no_report else write_report_files(report, Path(args.report_dir).expanduser().resolve())
        print_console_report(report, paths)
        sys.exit(1 if report["risk"] == "high" else 0)
    finally:
        if decode_temp_dir is not None:
            decode_temp_dir.cleanup()
        if source_temp_dir is not None:
            source_temp_dir.cleanup()


if __name__ == "__main__":
    main()
