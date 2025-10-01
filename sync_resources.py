#!/usr/bin/env python3
"""
Artsorakel Resource Sync Script
This script synchronizes shared resources between Android and iOS platforms
"""

import os
import sys
import subprocess
import json
import re
import csv
import shutil
import xml.etree.ElementTree as ET
import xml.sax.saxutils as saxutils
import html
from pathlib import Path
from typing import Dict, List, Tuple, Optional

# ANSI color codes
class Colors:
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    NC = '\033[0m'  # No Color

# Paths
SCRIPT_DIR = Path(__file__).parent.resolve()
SHARED_DIR = SCRIPT_DIR / 'shared'
ANDROID_DIR = SCRIPT_DIR / 'Android'
IOS_DIR = SCRIPT_DIR / 'iOS'


def print_section(message: str):
    print(f"{Colors.YELLOW}{message}{Colors.NC}")


def print_success(message: str):
    print(f"  ✅ {message}")


def print_error(message: str):
    print(f"  {Colors.RED}❌ {message}{Colors.NC}")


def print_warning(message: str):
    print(f"  {Colors.YELLOW}⚠️  {message}{Colors.NC}")


def print_info(message: str):
    print(f"  ℹ️  {message}")


def sync_config():
    """Sync configuration files and update version info"""
    print_section("📋 Syncing configuration...")

    config_file = SHARED_DIR / 'config' / 'app_config.json'
    secrets_file = SHARED_DIR / 'config' / 'secrets.json'

    # Check secrets file
    if not secrets_file.exists():
        print_warning("WARNING: secrets.json not found!")
        print(f"  {Colors.YELLOW}   Please copy secrets.json.template to secrets.json and add your bearer token.{Colors.NC}")
        print(f"  {Colors.YELLOW}   cp shared/config/secrets.json.template shared/config/secrets.json{Colors.NC}")
        print(f"  {Colors.YELLOW}   Then edit shared/config/secrets.json and replace YOUR_BEARER_TOKEN_HERE{Colors.NC}")
    else:
        print_success("Secrets file found")

    if not config_file.exists():
        print_error("Config file not found")
        return

    # Read config
    with open(config_file, 'r') as f:
        config = json.load(f)

    version = config['version']
    version_code = config['versionCode']
    api_url = config['api']['baseUrl']
    rss_feed_url = config['rssFeed']['url']

    # Update Android build.gradle.kts
    android_gradle = ANDROID_DIR / 'app' / 'build.gradle.kts'
    if android_gradle.exists():
        with open(android_gradle, 'r') as f:
            content = f.read()

        # Update version info
        content = re.sub(r'versionName = ".*?"', f'versionName = "{version}"', content)
        content = re.sub(r'versionCode = \d+', f'versionCode = {version_code}', content)
        content = re.sub(
            r'buildConfigField\("String", "API_BASE_URL", "\\".*?\\""\)',
            f'buildConfigField("String", "API_BASE_URL", "\\"{api_url}\\"")',
            content
        )
        content = re.sub(
            r'buildConfigField\("String", "RSS_FEED_URL", "\\"[^\\"]*\\""\)',
            f'buildConfigField("String", "RSS_FEED_URL", "\\"{rss_feed_url}\\"")',
            content
        )

        with open(android_gradle, 'w') as f:
            f.write(content)

        print_success(f"Updated Android version to {version} ({version_code})")
        print_success(f"Updated Android API URL to {api_url}")
        print_success(f"Updated Android RSS Feed URL to {rss_feed_url}")

    # Update iOS version
    ios_plist = IOS_DIR / 'Artsorakel' / 'Info.plist'
    if ios_plist.exists():
        # Try plistlib (available in Python 3)
        try:
            import plistlib
            with open(ios_plist, 'rb') as f:
                plist_data = plistlib.load(f)

            plist_data['CFBundleShortVersionString'] = version
            plist_data['CFBundleVersion'] = str(version_code)

            with open(ios_plist, 'wb') as f:
                plistlib.dump(plist_data, f)

            print_success(f"Updated iOS version to {version} ({version_code})")
        except Exception as e:
            print_warning(f"Could not update iOS plist: {e}")
    else:
        print_info("iOS uses modern project configuration (no Info.plist) - version managed in Xcode")


def sync_strings():
    """Sync localization strings from shared CSV to platform-specific formats"""
    print_section("🌐 Syncing localization strings from shared CSV...")

    csv_file = SHARED_DIR / 'strings.csv'
    if not csv_file.exists():
        print_error(f"CSV file not found at {csv_file}")
        return False

    # Read CSV
    with open(csv_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        rows = list(reader)

        if not rows:
            print_error("No data in CSV file")
            return False

        # Get language codes
        languages = [col for col in reader.fieldnames if col != 'key']

        # Process each language
        for lang in languages:
            print(f"  Processing language: {lang}")

            # Android language mapping
            android_lang_map = {
                'en': 'values',
                'nb': 'values-nb',
                'nn': 'values-nn',
                'nl': 'values-nl',
                'es': 'values-es',
                'sv': 'values-sv'
            }
            android_lang = android_lang_map.get(lang, f'values-{lang}')

            # Generate Android strings.xml
            android_strings_dir = ANDROID_DIR / 'app' / 'src' / 'main' / 'res' / android_lang
            android_strings_dir.mkdir(parents=True, exist_ok=True)

            with open(android_strings_dir / 'strings.xml', 'w', encoding='utf-8') as f:
                f.write('<?xml version="1.0" encoding="utf-8"?>\n')
                f.write('<resources>\n')
                f.write('    <!-- Auto-generated from shared CSV. Do not edit directly. -->\n')
                f.write('    <string name="app_name" translatable="false">Artsorakel</string>\n')

                for row in rows:
                    key = row['key']
                    value = row[lang]
                    if value:
                        escaped_value = saxutils.escape(value)
                        escaped_value = escaped_value.replace("&apos;", "\\'")
                        escaped_value = escaped_value.replace("'", "\\'")
                        f.write(f'    <string name="{key}">{escaped_value}</string>\n')

                f.write('</resources>\n')

            print_success(f"Generated Android strings for {lang}")

            # Generate iOS Localizable.strings
            ios_strings_dir = IOS_DIR / 'Artsorakel' / f'{lang}.lproj'
            ios_strings_dir.mkdir(parents=True, exist_ok=True)

            ios_resources_strings_dir = IOS_DIR / 'Artsorakel' / 'Resources' / 'Localizations' / f'{lang}.lproj'
            ios_resources_strings_dir.mkdir(parents=True, exist_ok=True)

            # Write to both locations
            for dir_path in [ios_strings_dir, ios_resources_strings_dir]:
                with open(dir_path / 'Localizable.strings', 'w', encoding='utf-8') as f:
                    f.write('/* Auto-generated from shared CSV. Do not edit directly. */\n')

                    for row in rows:
                        key = row['key']
                        value = row[lang]
                        if value:
                            escaped_value = value.replace('"', '\\"').replace('\n', '\\n')
                            f.write(f'"{key}" = "{escaped_value}";\n')

            print_success(f"Generated iOS strings for {lang}")

    print(f"\n{Colors.GREEN}✅ Successfully processed {len(languages)} languages from CSV{Colors.NC}")
    return True


def sync_images():
    """Sync images to both platforms"""
    print_section("🖼️  Syncing images...")

    # Ensure Android assets directory exists
    android_assets = ANDROID_DIR / 'app' / 'src' / 'main' / 'assets'
    android_assets.mkdir(parents=True, exist_ok=True)

    # Copy SVG logos to Android assets (exclude .inkscape.svg source files)
    images_dir = SHARED_DIR / 'images'
    if images_dir.exists():
        for svg in images_dir.glob('*.svg'):
            if not svg.name.endswith('.inkscape.svg'):
                shutil.copy(svg, android_assets)
                print_success(f"Copied {svg.name} to Android assets")

                # For iOS, copy to Resources/Images directory
                ios_images = IOS_DIR / 'Artsorakel' / 'Artsorakel' / 'Resources' / 'Images'
                ios_images.mkdir(parents=True, exist_ok=True)
                shutil.copy(svg, ios_images)
                print_success(f"Copied {svg.name} to iOS Resources/Images")

    # Copy vector SVGs from shared/vectors to iOS
    vectors_dir = SHARED_DIR / 'vectors'
    if vectors_dir.exists():
        ios_vectors = IOS_DIR / 'Artsorakel' / 'Artsorakel' / 'Resources' / 'Vectors'
        ios_vectors.mkdir(parents=True, exist_ok=True)

        for svg in vectors_dir.glob('*.svg'):
            if not svg.name.endswith('.inkscape.svg'):
                shutil.copy(svg, ios_vectors)
                print_success(f"Copied vector {svg.name} to iOS Resources/Vectors")


def check_svg_for_transforms(svg_file: Path) -> bool:
    """Check if SVG contains transform attributes"""
    with open(svg_file, 'r') as f:
        content = f.read()

    if 'transform=' in content:
        print_error(f"ERROR: SVG file '{svg_file.name}' contains transform attributes!")
        print(f"{Colors.YELLOW}   Transform attributes found:{Colors.NC}")

        # Show some examples
        for i, line in enumerate(content.split('\n')):
            if 'transform=' in line and i < 5:
                print(f"     {line.strip()}")

        print(f"\n{Colors.YELLOW}   The SVG file must be flattened before it can be converted to Android VectorDrawable.{Colors.NC}")
        print(f"{Colors.YELLOW}   Please use Inkscape to:{Colors.NC}")
        print(f"{Colors.YELLOW}     1. Open the file in Inkscape{Colors.NC}")
        print(f"{Colors.YELLOW}     2. Select all (Ctrl+A){Colors.NC}")
        print(f"{Colors.YELLOW}     3. Ungroup multiple times until nothing is grouped{Colors.NC}")
        print(f"{Colors.YELLOW}     4. Path → Object to Path{Colors.NC}")
        print(f"{Colors.YELLOW}     5. Path → Stroke to Path{Colors.NC}")
        print(f"{Colors.YELLOW}     6. Save as Optimized SVG with 10 decimal places{Colors.NC}\n")

        return False

    return True


def run_vdtool(input_file: Path, output_dir: Path) -> Optional[Path]:
    """Run vd-tool to convert SVG to Android VectorDrawable XML"""
    try:
        result = subprocess.run(
            ['vd-tool', '-c', '-in', str(input_file), '-out', str(output_dir)],
            check=True,
            capture_output=True,
            text=True
        )

        # vd-tool creates file with same base name but .xml extension
        expected_outputs = [
            output_dir / f"{input_file.stem}.xml",
            output_dir / f"{input_file.stem}.optimized.xml"
        ]

        for output in expected_outputs:
            if output.exists():
                return output

        return None
    except subprocess.CalledProcessError as e:
        # Check if it's a Java-related error
        if 'Java' in e.stderr or 'java' in e.stderr:
            return 'java_missing'
        return None
    except FileNotFoundError:
        return None


def sync_vectors():
    """Sync vector assets (SVG → Android VectorDrawable XML)"""
    print_section("🎨 Syncing vector assets (SVG → Android VectorDrawable XML)...")

    drawable_dir = ANDROID_DIR / 'app' / 'src' / 'main' / 'res' / 'drawable'
    drawable_night_dir = ANDROID_DIR / 'app' / 'src' / 'main' / 'res' / 'drawable-night'
    drawable_dir.mkdir(parents=True, exist_ok=True)
    drawable_night_dir.mkdir(parents=True, exist_ok=True)

    # Check if vd-tool is available
    vdtool_available = shutil.which('vd-tool') is not None

    # Generate launcher icon background
    print("  🚀 Generating launcher icon drawables...")

    launcher_bg = drawable_dir / 'ic_launcher_background.xml'
    launcher_bg.write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#394244"
        android:pathData="M0,0h108v108h-108z" />
</vector>
''')
    print_success("Generated ic_launcher_background.xml")

    # Generate launcher foreground from logo
    logo_svg = SHARED_DIR / 'images' / 'ic_logo_color_dark.svg'
    if not logo_svg.exists():
        print_error(f"ERROR: Logo SVG not found at {logo_svg}")
        print_error("   Cannot generate launcher foreground without logo SVG")
        sys.exit(1)

    if not vdtool_available:
        print_warning("vd-tool not found - skipping vector conversions")
        print(f"  {Colors.YELLOW}   Install with: npm install -g vd-tool{Colors.NC}")
        return True

    print("    🔄 Converting logo to launcher foreground...")
    result = run_vdtool(logo_svg, drawable_dir)

    if result == 'java_missing':
        print_warning("vd-tool requires Java - skipping vector conversions")
        print(f"  {Colors.YELLOW}   Install Java from: https://www.java.com{Colors.NC}")
        return True
    elif not result:
        print_warning("vd-tool conversion failed for launcher foreground")
        print(f"  {Colors.YELLOW}   Unable to convert {logo_svg}{Colors.NC}")
        return True

    # Rename to launcher foreground
    launcher_fg = drawable_dir / 'ic_launcher_foreground.xml'
    result.rename(launcher_fg)

    # Adjust the vector drawable to work as adaptive icon foreground
    with open(launcher_fg, 'r') as f:
        content = f.read()

    # Replace viewport dimensions
    content = re.sub(r'android:width="[^"]*"', 'android:width="108dp"', content)
    content = re.sub(r'android:height="[^"]*"', 'android:height="108dp"', content)
    content = re.sub(r'android:viewportWidth="[^"]*"', 'android:viewportWidth="108"', content)
    content = re.sub(r'android:viewportHeight="[^"]*"', 'android:viewportHeight="108"', content)

    with open(launcher_fg, 'w') as f:
        f.write(content)

    print_success("Generated ic_launcher_foreground.xml from logo")

    # Process light/dark SVG pairs
    converted_any = False
    conversion_failed = False

    images_dir = SHARED_DIR / 'images'
    if images_dir.exists():
        for light_svg in list(images_dir.glob('*_light.svg')) + list(images_dir.glob('*_light.optimized.svg')):
            # Extract base name
            if light_svg.name.endswith('_light.optimized.svg'):
                base_name = light_svg.name[:-len('_light.optimized.svg')]
                is_optimized = True
            else:
                base_name = light_svg.name[:-len('_light.svg')]
                is_optimized = False

            # Convert to Android resource name
            base_name_lower = base_name.lower().replace('-', '_')
            android_name = base_name_lower if base_name_lower.startswith('ic_') else f'ic_{base_name_lower}'

            print(f"  🔄 Processing {light_svg.name}...")

            # Check and convert light version
            if not check_svg_for_transforms(light_svg):
                print_error(f"   ⛔ Aborting conversion of {light_svg.name}")
                conversion_failed = True
                continue

            light_output = drawable_dir / f'{android_name}.xml'
            result = run_vdtool(light_svg, drawable_dir)

            if result:
                if result != light_output:
                    result.rename(light_output)
                converted_any = True
                print_success(f"Generated {light_output.name}")

                # Look for dark version
                dark_svg_candidates = [
                    images_dir / f'{base_name}_dark.optimized.svg',
                    images_dir / f'{base_name}_dark.svg'
                ]

                dark_svg = None
                for candidate in dark_svg_candidates:
                    if candidate.exists():
                        dark_svg = candidate
                        break

                if dark_svg:
                    if not check_svg_for_transforms(dark_svg):
                        print_error(f"   ⛔ Aborting conversion of {dark_svg.name}")
                        conversion_failed = True
                        continue

                    dark_output = drawable_night_dir / f'{android_name}.xml'
                    result = run_vdtool(dark_svg, drawable_night_dir)

                    if result:
                        if result != dark_output:
                            result.rename(dark_output)
                        print_success(f"Generated light/dark pair for {android_name}")
                    else:
                        conversion_failed = True
                else:
                    print_warning(f"No dark variant found for {base_name}")
            else:
                conversion_failed = True

    if conversion_failed:
        print_error("⛔ Some conversions failed due to transform attributes. Please fix the SVG files first.")
        return False

    if not converted_any:
        print_warning("No light/dark SVG pairs found to convert in shared/images/")

    # Process other vector files
    vectors_dir = SHARED_DIR / 'vectors'
    if vectors_dir.exists():
        for svg in vectors_dir.glob('*.svg'):
            # Replace Norwegian characters for Android drawable names
            name = svg.stem
            name = name.replace('æ', 'ae').replace('ø', 'oe').replace('å', 'aa')
            name = name.replace('Æ', 'Ae').replace('Ø', 'Oe').replace('Å', 'Aa')

            out = drawable_dir / f'{name}.xml'

            print(f"  🔄 Converting {svg.name} → {out.name}")

            if not check_svg_for_transforms(svg):
                print_error(f"   ⛔ Skipping conversion of {svg.name}")
                continue

            if vdtool_available:
                result = run_vdtool(svg, drawable_dir)

                if result and result != out:
                    result.rename(out)

                if out.exists():
                    print_success(f"Generated {out.name}")
                else:
                    print_error(f"Failed to convert {svg.name}")
            else:
                print_warning("vd-tool not found. Install with: npm install -g vd-tool")

    return True


def sync_fonts():
    """Sync fonts to both platforms"""
    print_section("🔤 Syncing fonts...")

    fonts_dir = SHARED_DIR / 'fonts'
    if not fonts_dir.exists():
        return

    # Copy to Android
    android_fonts = ANDROID_DIR / 'app' / 'src' / 'main' / 'res' / 'font'
    android_fonts.mkdir(parents=True, exist_ok=True)

    for font in fonts_dir.glob('*.ttf'):
        shutil.copy(font, android_fonts)
        print_success(f"Copied {font.name} to Android fonts")

    # Copy to iOS
    ios_fonts = IOS_DIR / 'Artsorakel' / 'Artsorakel' / 'Resources' / 'Fonts'
    ios_fonts.mkdir(parents=True, exist_ok=True)

    for font in fonts_dir.glob('*.ttf'):
        shutil.copy(font, ios_fonts)
        print_success(f"Copied {font.name} to iOS Resources/Fonts")


def sync_content():
    """Sync HTML content and FAQ JSON files"""
    print_section("📄 Syncing HTML content...")

    content_dir = SHARED_DIR / 'content'
    if not content_dir.exists():
        return

    # Ensure Android assets directory exists
    android_assets = ANDROID_DIR / 'app' / 'src' / 'main' / 'assets'
    android_assets.mkdir(parents=True, exist_ok=True)

    # Copy FAQ JSON files to Android
    for json_file in content_dir.glob('faq_*.json'):
        shutil.copy(json_file, android_assets)
        print_success(f"Copied {json_file.name} to Android assets")

    # Copy other HTML files to Android (non-FAQ)
    for html_file in content_dir.glob('*.html'):
        if not html_file.name.startswith('faq_'):
            shutil.copy(html_file, android_assets)
            print_success(f"Copied {html_file.name} to Android assets")

    # Copy HTML files to iOS (non-FAQ)
    ios_content = IOS_DIR / 'Artsorakel' / 'Resources' / 'Content'
    ios_content.mkdir(parents=True, exist_ok=True)

    for html_file in content_dir.glob('*.html'):
        if not html_file.name.startswith('faq_'):
            shutil.copy(html_file, ios_content)
            print_success(f"Copied {html_file.name} to iOS resources")

    # Copy FAQ JSON files to iOS
    for json_file in content_dir.glob('faq_*.json'):
        shutil.copy(json_file, ios_content)
        print_success(f"Copied {json_file.name} to iOS resources")


def sync_design_system():
    """Sync design system colors and themes"""
    print_section("🎨 Syncing design system colors and themes...")

    design_dir = SHARED_DIR / 'designsystem'
    if not design_dir.exists():
        print_warning("Design system directory not found")
        return

    # Process Variables primitives.txt to generate colors.xml
    vars_file = design_dir / 'Variables primitives.txt'
    if vars_file.exists():
        print("  🔄 Processing Variables primitives.txt...")

        content = vars_file.read_text()

        # Extract color variables
        color_map = {}
        pattern = r'--([a-z-]+(?:-\d+|-base-positive|-base-negative|-base)?)\s*:\s*(#[A-F0-9]{6})'
        matches = re.findall(pattern, content, re.IGNORECASE)

        for name, value in matches:
            # Convert CSS var name to Android resource name
            android_name = name.replace('-', '_')
            color_map[android_name] = value.upper()

        # Read Variables listcategories.txt if it exists
        listcat_file = design_dir / 'Variables listcategories.txt'
        if listcat_file.exists():
            listcat_content = listcat_file.read_text()

            # Extract invasive and redlist category colors
            listcat_pattern = r'--((?:invasive|redlist)-[a-z]+)\s*:\s*(#[A-F0-9]{6})'
            listcat_matches = re.findall(listcat_pattern, listcat_content, re.IGNORECASE)

            for name, value in listcat_matches:
                # Convert CSS var name to Android resource name
                android_name = name.replace('-', '_')
                color_map[android_name] = value.upper()

            print_success(f"Found {len(listcat_matches)} list category colors")

        # Read existing colors.xml from designsystem
        existing_colors = {}
        colors_file = design_dir / 'colors.xml'
        if colors_file.exists():
            colors_content = colors_file.read_text()
            # Extract existing colors
            color_pattern = r'<color name="([^"]+)">([^<]+)</color>'
            for match in re.findall(color_pattern, colors_content):
                existing_colors[match[0]] = match[1]

        # Read functional colors
        func_colors_file = design_dir / 'functional_colors.xml'
        if func_colors_file.exists():
            func_content = func_colors_file.read_text()
            # Extract functional colors
            color_pattern = r'<color name="([^"]+)">([^<]+)</color>'
            for match in re.findall(color_pattern, func_content):
                existing_colors[match[0]] = match[1]

        # Merge new primitive colors with existing
        all_colors = {**color_map, **existing_colors}

        # Generate colors.xml
        colors_xml = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'

        # Add primitive colors from Variables
        colors_xml += '    <!-- Primitive colors from design system -->\n'
        for name, value in sorted(color_map.items()):
            colors_xml += f'    <color name="{name}">{value}</color>\n'

        colors_xml += '\n    <!-- Existing colors -->\n'
        # Add remaining colors from the existing file (avoiding duplicates)
        for name, value in sorted(existing_colors.items()):
            if name not in color_map:
                colors_xml += f'    <color name="{name}">{value}</color>\n'

        colors_xml += '</resources>\n'

        # Write to Android colors.xml
        android_colors_dir = ANDROID_DIR / 'app' / 'src' / 'main' / 'res' / 'values'
        android_colors_dir.mkdir(parents=True, exist_ok=True)
        android_colors_file = android_colors_dir / 'colors.xml'
        android_colors_file.write_text(colors_xml)

        print_success(f"Generated colors.xml with {len(all_colors)} colors")

    # Process Semantic tokens.txt to add semantic color references and generate themes
    semantic_file = design_dir / 'Semantic tokens.txt'
    if semantic_file.exists() and vars_file.exists():
        print("  🔄 Processing Semantic tokens and generating themes...")

        # First, read all primitive colors from Variables primitives.txt
        primitives = {}
        content = vars_file.read_text()
        pattern = r'--([a-z-]+(?:-\d+|-base-positive|-base-negative|-base)?)\s*:\s*(#[A-F0-9]{6})'
        for match in re.findall(pattern, content, re.IGNORECASE):
            name = match[0].replace('-', '_')
            primitives[name] = match[1].upper()

        # Read the Semantic tokens file
        content = semantic_file.read_text()

        # Extract semantic tokens for light and dark modes
        light_tokens = {}
        dark_tokens = {}

        # Parse light mode tokens
        light_section = re.search(r':root\s*{([^}]+)}', content, re.DOTALL)
        if light_section:
            # Match both var() references and direct primitives - include numbers in pattern
            token_pattern = r'--([a-z0-9-]+)\s*:\s*(?:var\(--([a-z0-9-]+)\)|([^;]+))'
            for match in re.findall(token_pattern, light_section.group(1)):
                token_name = match[0].replace('-', '_')
                if match[1]:  # var() reference
                    ref_name = match[1].replace('-', '_')
                    light_tokens[token_name] = ref_name
                elif match[2]:  # direct value
                    light_tokens[token_name] = match[2].strip()

        # Parse dark mode tokens
        dark_section = re.search(r'\[data-theme="dark"\]\s*{([^}]+)}', content, re.DOTALL)
        if dark_section:
            token_pattern = r'--([a-z0-9-]+)\s*:\s*(?:var\(--([a-z0-9-]+)\)|([^;]+))'
            for match in re.findall(token_pattern, dark_section.group(1)):
                token_name = match[0].replace('-', '_')
                if match[1]:  # var() reference
                    ref_name = match[1].replace('-', '_')
                    dark_tokens[token_name] = ref_name
                elif match[2]:  # direct value
                    dark_tokens[token_name] = match[2].strip()

        # Create attrs.xml with all semantic attributes
        attrs_xml = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        attrs_xml += '    <!-- Semantic design tokens -->\n'

        # Get all unique token names
        all_token_names = set(list(light_tokens.keys()) + list(dark_tokens.keys()))
        for token in sorted(all_token_names):
            attrs_xml += f'    <attr name="{token}" format="color|reference" />\n'

        # Add existing RSS attributes
        attrs_xml += '\n    <!-- RSS Category attributes -->\n'
        rss_attrs = [
            'rssDangerBackground', 'rssDangerBorder', 'rssDangerText',
            'rssWarningBackground', 'rssWarningBorder', 'rssWarningText',
            'rssInfoBackground', 'rssInfoBorder', 'rssInfoText'
        ]
        for attr in rss_attrs:
            attrs_xml += f'    <attr name="{attr}" format="color|reference" />\n'

        # Add app-specific attributes from app_attrs.xml if it exists
        app_attrs_file = design_dir / 'app_attrs.xml'
        if app_attrs_file.exists():
            import xml.etree.ElementTree as ET
            tree = ET.parse(app_attrs_file)
            root = tree.getroot()
            for attr in root.findall('.//attr'):
                attr_name = attr.get('name')
                attr_format = attr.get('format', 'color|reference')
                if attr_name not in all_token_names and attr_name not in rss_attrs:
                    attrs_xml += f'    <attr name="{attr_name}" format="{attr_format}" />\n'

        attrs_xml += '</resources>\n'

        # Write attrs.xml
        attrs_file = ANDROID_DIR / 'app' / 'src' / 'main' / 'res' / 'values' / 'attrs.xml'
        attrs_file.write_text(attrs_xml)
        print_success("Generated attrs.xml with semantic attributes")

        # Helper function to resolve color references
        def resolve_color_reference(token_value, primitives, light_tokens, dark_tokens):
            """Resolve a color reference to an actual color resource"""
            # Skip numeric values - they can't be color resources
            try:
                float(token_value)
                return None
            except (ValueError, TypeError):
                pass

            # If it references another token, resolve that first
            if token_value in light_tokens or token_value in dark_tokens:
                # This is a reference to another semantic token, use attr reference
                return f"?attr/{token_value}"

            # If it's a primitive color
            if token_value in primitives:
                return f"@color/{token_value}"

            # If it's a direct color value
            if token_value.startswith('#'):
                return token_value
            else:
                # Default to trying as a color reference
                return f"@color/{token_value}"

        # Read the shared theme file
        shared_theme_file = design_dir / 'themes_shared.xml'

        # Generate light theme
        if shared_theme_file.exists():
            import xml.etree.ElementTree as ET
            tree = ET.parse(shared_theme_file)
            root = tree.getroot()

            # Find the Theme.Artsorakel style element
            for style in root.findall('.//style[@name="Theme.Artsorakel"]'):
                # Add semantic token mappings for light mode
                for token_name, ref_value in light_tokens.items():
                    # Check if item already exists
                    existing = False
                    for item in style.findall('item'):
                        if item.get('name') == token_name:
                            existing = True
                            break

                    if not existing:
                        # Add the new item if it has a valid color reference
                        resolved = resolve_color_reference(ref_value, primitives, light_tokens, dark_tokens)
                        if resolved:
                            new_item = ET.SubElement(style, 'item')
                            new_item.set('name', token_name)
                            new_item.text = resolved

            # Write the light theme with proper formatting
            ET.indent(tree, space="    ", level=0)
            themes_file = ANDROID_DIR / 'app' / 'src' / 'main' / 'res' / 'values' / 'themes.xml'
            tree.write(themes_file, encoding='utf-8', xml_declaration=True)
            print_success("Generated light theme with semantic tokens")

            # Generate dark theme from the same shared base
            dark_tree = ET.parse(shared_theme_file)
            dark_root = dark_tree.getroot()

            # Find the Theme.Artsorakel style element
            for style in dark_root.findall('.//style[@name="Theme.Artsorakel"]'):
                # Replace/add semantic token mappings for dark mode
                for token_name, ref_value in dark_tokens.items():
                    # Check if item already exists
                    existing = False
                    for item in style.findall('item'):
                        if item.get('name') == token_name:
                            # Update existing item
                            resolved = resolve_color_reference(ref_value, primitives, light_tokens, dark_tokens)
                            if resolved:
                                item.text = resolved
                            existing = True
                            break

                    if not existing:
                        # Add the new item if it has a valid color reference
                        resolved = resolve_color_reference(ref_value, primitives, light_tokens, dark_tokens)
                        if resolved:
                            new_item = ET.SubElement(style, 'item')
                            new_item.set('name', token_name)
                            new_item.text = resolved

            # Write the dark theme with proper formatting
            ET.indent(dark_tree, space="    ", level=0)
            dark_dir = ANDROID_DIR / 'app' / 'src' / 'main' / 'res' / 'values-night'
            dark_dir.mkdir(parents=True, exist_ok=True)
            dark_themes_file = dark_dir / 'themes.xml'
            dark_tree.write(dark_themes_file, encoding='utf-8', xml_declaration=True)
            print_success("Generated dark theme with semantic tokens")
        else:
            print_warning(f"themes_shared.xml not found in {design_dir}")

    # Generate iOS color assets from design system
    if semantic_file.exists() and vars_file.exists():
        print("  🔄 Generating iOS color assets...")

        # First, read all primitive colors from Variables primitives.txt
        primitives = {}
        content = vars_file.read_text()
        pattern = r'--([a-z-]+(?:-\d+|-base-positive|-base-negative|-base)?)\s*:\s*(#[A-F0-9]{6})'
        for match in re.findall(pattern, content, re.IGNORECASE):
            name = match[0].replace('-', '_')
            primitives[name] = match[1].upper()

        # Read the Semantic tokens file
        content = semantic_file.read_text()

        # Extract semantic tokens for light and dark modes
        light_tokens = {}
        dark_tokens = {}

        # Parse light mode tokens
        light_section = re.search(r':root\s*{([^}]+)}', content, re.DOTALL)
        if light_section:
            # Match both var() references and direct primitives - include numbers in pattern
            token_pattern = r'--([a-z0-9-]+)\s*:\s*(?:var\(--([a-z0-9-]+)\)|([^;]+))'
            for match in re.findall(token_pattern, light_section.group(1)):
                token_name = match[0].replace('-', '_')
                if match[1]:  # var() reference
                    ref_name = match[1].replace('-', '_')
                    light_tokens[token_name] = ref_name
                elif match[2]:  # direct value
                    light_tokens[token_name] = match[2].strip()

        # Parse dark mode tokens
        dark_section = re.search(r'\[data-theme="dark"\]\s*{([^}]+)}', content, re.DOTALL)
        if dark_section:
            token_pattern = r'--([a-z0-9-]+)\s*:\s*(?:var\(--([a-z0-9-]+)\)|([^;]+))'
            for match in re.findall(token_pattern, dark_section.group(1)):
                token_name = match[0].replace('-', '_')
                if match[1]:  # var() reference
                    ref_name = match[1].replace('-', '_')
                    dark_tokens[token_name] = ref_name
                elif match[2]:  # direct value
                    dark_tokens[token_name] = match[2].strip()

        # Get all unique token names
        all_token_names = set(list(light_tokens.keys()) + list(dark_tokens.keys()))

        # Helper function to resolve iOS color values
        def resolve_ios_color(token_value, primitives, tokens):
            # Direct hex value
            if isinstance(token_value, str) and token_value.startswith('#'):
                return token_value

            # Check tokens first
            if token_value in tokens:
                ref_value = tokens[token_value]
                # Recursively resolve if it's another reference
                if ref_value in tokens:
                    return resolve_ios_color(ref_value, primitives, tokens)
                elif ref_value in primitives:
                    return primitives[ref_value]
                elif isinstance(ref_value, str) and ref_value.startswith('#'):
                    return ref_value

            # Check primitives with underscore version
            if token_value in primitives:
                return primitives[token_value]

            # Try with underscores instead of hyphens
            underscored = token_value.replace('-', '_') if isinstance(token_value, str) else str(token_value)
            if underscored in primitives:
                return primitives[underscored]

            return None

        # Generate iOS colors from ALL semantic tokens - prefixed with Color_ to avoid conflicts
        ios_colors = {}
        for token_name in all_token_names:
            # Convert token name to iOS color name (e.g., background_default -> Color_backgroundDefault)
            # Use camelCase for the color name part
            parts = token_name.split('_')
            camel_case = parts[0] + ''.join(word.capitalize() for word in parts[1:])
            ios_name = f'Color_{camel_case}'
            ios_colors[ios_name] = (token_name, token_name)

        assets_dir = IOS_DIR / 'Artsorakel' / 'Assets.xcassets'
        assets_dir.mkdir(parents=True, exist_ok=True)

        for ios_name, (light_token, dark_token) in ios_colors.items():
            colorset_dir = assets_dir / f'{ios_name}.colorset'
            colorset_dir.mkdir(parents=True, exist_ok=True)

            # Get actual colors
            light_ref = light_tokens.get(light_token)
            dark_ref = dark_tokens.get(dark_token)

            light_color = resolve_ios_color(light_ref, primitives, light_tokens)
            dark_color = resolve_ios_color(dark_ref, primitives, dark_tokens)

            if light_color and dark_color:
                # Convert hex to RGB
                def hex_to_rgb(hex_color):
                    hex_color = hex_color.lstrip('#')
                    return tuple(int(hex_color[i:i+2], 16) / 255.0 for i in (0, 2, 4))

                light_rgb = hex_to_rgb(light_color)
                dark_rgb = hex_to_rgb(dark_color)

                contents = {
                    "colors": [
                        {
                            "color": {
                                "color-space": "srgb",
                                "components": {
                                    "alpha": "1.000",
                                    "blue": f"{light_rgb[2]:.3f}",
                                    "green": f"{light_rgb[1]:.3f}",
                                    "red": f"{light_rgb[0]:.3f}"
                                }
                            },
                            "idiom": "universal"
                        },
                        {
                            "appearances": [
                                {
                                    "appearance": "luminosity",
                                    "value": "dark"
                                }
                            ],
                            "color": {
                                "color-space": "srgb",
                                "components": {
                                    "alpha": "1.000",
                                    "blue": f"{dark_rgb[2]:.3f}",
                                    "green": f"{dark_rgb[1]:.3f}",
                                    "red": f"{dark_rgb[0]:.3f}"
                                }
                            },
                            "idiom": "universal"
                        }
                    ],
                    "info": {
                        "author": "xcode",
                        "version": 1
                    }
                }

                contents_file = colorset_dir / 'Contents.json'
                contents_file.write_text(json.dumps(contents, indent=2))
                print_success(f"Generated {ios_name}.colorset")

        print_success("Generated iOS color assets from design system")


def sync_app_icons():
    """Generate app icons for iOS"""
    print_section("🎨 Generating app icons...")

    # Check Android launcher icons
    launcher_fg = ANDROID_DIR / 'app' / 'src' / 'main' / 'res' / 'drawable' / 'ic_launcher_foreground.xml'
    launcher_bg = ANDROID_DIR / 'app' / 'src' / 'main' / 'res' / 'drawable' / 'ic_launcher_background.xml'

    if launcher_fg.exists() and launcher_bg.exists():
        print_success("Android launcher icons already generated from logo SVG")
    else:
        print_warning("Android launcher icons not found - check sync_vectors function")

    # Generate iOS app icons
    print("  🔄 Generating iOS app icons...")

    ios_icon_dir = IOS_DIR / 'Artsorakel' / 'Artsorakel' / 'Assets.xcassets' / 'AppIcon.appiconset'
    ios_icon_dir.mkdir(parents=True, exist_ok=True)

    source_svg = SHARED_DIR / 'images' / 'ic_logo_color_dark.svg'
    light_svg = SHARED_DIR / 'images' / 'ic_logo_color_light.svg'

    if not source_svg.exists():
        print_error(f"Logo SVG not found at {source_svg}")
        return

    # Check for ImageMagick or rsvg-convert
    has_convert = shutil.which('convert') is not None
    has_rsvg = shutil.which('rsvg-convert') is not None

    if has_convert:
        print("  🎨 Using ImageMagick to generate iOS app icons...")

        # Generate light appearance icon
        if light_svg.exists():
            subprocess.run([
                'convert', '-background', '#FFFFFF', str(light_svg),
                '-gravity', 'center', '-resize', '800x800', '-extent', '1024x1024',
                str(ios_icon_dir / 'AppIcon.png')
            ], check=True, capture_output=True)
            print_success("Generated AppIcon.png (light appearance)")

        # Generate dark appearance icon
        subprocess.run([
            'convert', '-background', '#394244', str(source_svg),
            '-gravity', 'center', '-resize', '800x800', '-extent', '1024x1024',
            str(ios_icon_dir / 'AppIcon~dark.png')
        ], check=True, capture_output=True)
        print_success("Generated AppIcon~dark.png (dark appearance)")

        # Generate tinted appearance icon
        subprocess.run([
            'convert', str(source_svg), '-colorspace', 'Gray', '-negate',
            '-background', '#FFFFFF', '-gravity', 'center', '-resize', '800x800', '-extent', '1024x1024',
            str(ios_icon_dir / 'AppIcon~tinted.png')
        ], check=True, capture_output=True)
        print_success("Generated AppIcon~tinted.png (tinted appearance)")

        # Write Contents.json with all three icons
        contents_json = ios_icon_dir / 'Contents.json'
        contents_json.write_text('''{
  "images" : [
    {
      "filename" : "AppIcon.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "filename" : "AppIcon~dark.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "tinted"
        }
      ],
      "filename" : "AppIcon~tinted.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
''')
        print_success("Updated Contents.json with icon filenames")
        print(f"  {Colors.GREEN}✅ iOS app icons generated successfully{Colors.NC}")

    elif has_rsvg:
        print("  🎨 Using rsvg-convert to generate iOS app icons...")

        # Generate light appearance
        if light_svg.exists():
            subprocess.run([
                'rsvg-convert', '-w', '1024', '-h', '1024', '--background-color=#FFFFFF',
                str(light_svg), '-o', str(ios_icon_dir / 'AppIcon.png')
            ], check=True, capture_output=True)
            print_success("Generated AppIcon.png (light appearance)")

        # Generate dark appearance
        subprocess.run([
            'rsvg-convert', '-w', '1024', '-h', '1024', '--background-color=#394244',
            str(source_svg), '-o', str(ios_icon_dir / 'AppIcon~dark.png')
        ], check=True, capture_output=True)
        print_success("Generated AppIcon~dark.png (dark appearance)")

        print_warning("Tinted icon generation requires ImageMagick")

        # Write Contents.json with two icons
        contents_json = ios_icon_dir / 'Contents.json'
        contents_json.write_text('''{
  "images" : [
    {
      "filename" : "AppIcon.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "filename" : "AppIcon~dark.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
''')
        print_success("Updated Contents.json with icon filenames")
        print(f"  {Colors.GREEN}✅ iOS app icons generated (without tinted variant){Colors.NC}")

    else:
        print_warning("No SVG to PNG converter found. Install ImageMagick or rsvg-convert to generate iOS icons")
        print(f"{Colors.YELLOW}   Install with: apt-get install imagemagick (Linux) or brew install imagemagick (macOS){Colors.NC}")


def validate_sync() -> bool:
    """Validate that sync completed successfully"""
    print_section("✔️  Validating sync...")

    errors = 0

    # Check Android
    android_gradle = ANDROID_DIR / 'app' / 'build.gradle.kts'
    if not android_gradle.exists():
        print_error("Android build.gradle.kts not found")
        errors += 1
    else:
        print_success("Android project structure valid")

    # Check iOS
    ios_project = IOS_DIR / 'Artsorakel' / 'Artsorakel.xcodeproj' / 'project.pbxproj'
    ios_app_dir = IOS_DIR / 'Artsorakel'

    if not ios_project.exists():
        print_error("iOS project.pbxproj not found")
        errors += 1
    elif not ios_app_dir.exists():
        print_error("iOS app target directory not found")
        errors += 1
    else:
        print_success("iOS project structure valid")

    if errors == 0:
        print(f"  {Colors.GREEN}✅ Validation passed{Colors.NC}")
        return True
    else:
        print_error(f"Validation failed with {errors} errors")
        return False


def main():
    """Main execution"""
    print(f"{Colors.GREEN}🔄 Starting resource synchronization...{Colors.NC}")

    # Check dependencies
    if shutil.which('jq') is None:
        print_error("jq is required but not installed. Install it with: apt-get install jq (Linux) or brew install jq (macOS)")
        sys.exit(1)

    # Run sync functions
    sync_config()
    sync_strings()
    sync_images()
    sync_vectors()
    sync_fonts()
    sync_content()
    sync_design_system()
    sync_app_icons()

    # Validate
    if validate_sync():
        print(f"{Colors.GREEN}✨ Resource synchronization completed successfully!{Colors.NC}")
    else:
        print(f"{Colors.YELLOW}⚠️  Synchronization completed with warnings{Colors.NC}")


if __name__ == '__main__':
    main()
