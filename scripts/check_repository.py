"""Check the documentation-stage foundation, not app correctness or all secrets."""

from pathlib import Path
import re
import hashlib
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = (
    "README.md", "CHANGELOG.md", "SECURITY.md", "CONTRIBUTING.md", "docs/DESIGN.md",
    "docs/RELEASE_CHECKLIST.md", ".gitignore", ".editorconfig",
    ".github/PULL_REQUEST_TEMPLATE.md", ".github/ISSUE_TEMPLATE/bug_report.md",
    ".github/ISSUE_TEMPLATE/feature_request.md",
    ".github/workflows/repository-checks.yml",
)


def check() -> list[str]:
    errors: list[str] = []
    for relative in REQUIRED:
        path = ROOT / relative
        if not path.is_file() or not path.read_text(encoding="utf-8").strip():
            errors.append(f"Missing or empty required file: {relative}")
    for path in ROOT.rglob("*.md"):
        if ".git" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        for target in re.findall(r"\]\(([^)]+)\)", text):
            if ":" in target or target.startswith("#"):
                continue
            local = target.split("#", 1)[0]
            if local and not (path.parent / local).exists():
                # Pages under docs/wiki/ are GitHub Wiki source: they use
                # extensionless page-name links ([x](Home)) that resolve to
                # <name>.md here and to the wiki page after sync.
                if path.relative_to(ROOT).parts[:2] == ("docs", "wiki") and (
                    path.parent / f"{local}.md"
                ).is_file():
                    continue
                errors.append(f"Broken local link in {path.relative_to(ROOT)}: {target}")
    gradle = ROOT / "app" / "build.gradle.kts"
    changelog = ROOT / "CHANGELOG.md"
    if gradle.is_file() and changelog.is_file():
        match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle.read_text(encoding="utf-8"))
        if match:
            base = match.group(1).split("-", 1)[0]
            if f"## [{base}]" not in changelog.read_text(encoding="utf-8"):
                errors.append(f"CHANGELOG.md missing entry for version {base}")
    release_workflow = ROOT / ".github" / "workflows" / "release.yml"
    if release_workflow.is_file():
        workflow_text = release_workflow.read_text(encoding="utf-8")
        # Any explicit push of the tag by name satisfies this (origin or an
        # authenticated URL remote); --follow-tags never does, since it skips
        # lightweight tags.
        explicit_tag_push = re.search(r'git push\s+\S+\s+"v\$\{NEW_VERSION\}"', workflow_text)
        if not explicit_tag_push or "--follow-tags" in workflow_text:
            errors.append("release.yml must push the release tag explicitly; --follow-tags skips lightweight tags")
    readme = (ROOT / "README.md").read_text(encoding="utf-8") if (ROOT / "README.md").exists() else ""
    for milestone in range(7):
        if f"M{milestone}" not in readme:
            errors.append(f"README missing M{milestone}")
    for path in ROOT.rglob("*"):
        if not path.is_file() or ".git" in path.parts:
            continue
        if path.suffix.lower() in {".jks", ".keystore", ".p12", ".apk", ".aab", ".db"} or path.name == ".env":
            errors.append(f"Forbidden sensitive/generated file: {path.relative_to(ROOT)}")
    wrapper = ROOT / "gradle/wrapper/gradle-wrapper.jar"
    expected_wrapper = "3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98"
    if not wrapper.is_file() or hashlib.sha256(wrapper.read_bytes()).hexdigest() != expected_wrapper:
        errors.append("Gradle wrapper JAR must match the pinned upstream SHA256")
    # Launcher resources are an app identity, not the Quick Settings tile.
    manifest = ROOT / "app/src/main/AndroidManifest.xml"
    android = "{http://schemas.android.com/apk/res/android}"
    if manifest.is_file():
        application = ET.parse(manifest).getroot().find("application")
        for attr, name in (("icon", "ic_launcher"), ("roundIcon", "ic_launcher_round")):
            if application is None or application.get(android + attr) != "@mipmap/" + name:
                errors.append("Application must declare launcher " + attr)
            icon = ROOT / f"app/src/main/res/mipmap-anydpi-v26/{name}.xml"
            if not icon.is_file():
                errors.append("Missing adaptive launcher icon: " + name)
                continue
            adaptive = ET.parse(icon).getroot()
            for layer in ("background", "foreground", "monochrome"):
                node = adaptive.find(layer)
                resource = node.get(android + "drawable", "") if node is not None else ""
                if not resource.startswith("@drawable/") or not (
                    ROOT / "app/src/main/res/drawable" / (resource.removeprefix("@drawable/") + ".xml")
                ).is_file():
                    errors.append(f"Launcher {name} needs a valid {layer} resource")
    # The recorded live flow must use the shipped palette, not M3 baseline purple.
    live_test = ROOT / "app/src/androidTest/java/il/sidewalks/reporter/live/LiveEmulatorSubmissionTest.kt"
    if live_test.is_file():
        live_source = live_test.read_text(encoding="utf-8")
        if ("import il.sidewalks.reporter.ui.theme.AppTheme" not in live_source
                or not re.search(r"compose\.setContent\s*\{\s*AppTheme\s*\{", live_source)
                or re.search(r"\bMaterialTheme\s*\{", live_source)):
            errors.append("Live emulator flow must render with AppTheme, never baseline MaterialTheme")
    return errors


if __name__ == "__main__":
    failures = check()
    for failure in failures:
        print(failure, file=sys.stderr)
    if failures:
        sys.exit(1)
    print("Scaffolding checks passed. This script does not prove Android build/unit/e2e results.")
