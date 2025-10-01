#!/bin/bash

# Artsorakel Resource Sync Script
# This script synchronizes shared resources between Android and iOS platforms

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHARED_DIR="$SCRIPT_DIR/shared"
ANDROID_DIR="$SCRIPT_DIR/Android"
IOS_DIR="$SCRIPT_DIR/iOS"

echo -e "${GREEN}🔄 Starting resource synchronization...${NC}"

# Function to sync config
sync_config() {
    echo -e "${YELLOW}📋 Syncing configuration...${NC}"

    CONFIG_FILE="$SHARED_DIR/config/app_config.json"
    SECRETS_FILE="$SHARED_DIR/config/secrets.json"

    # Check if secrets file exists
    if [ ! -f "$SECRETS_FILE" ]; then
        echo -e "  ${RED}⚠️  WARNING: secrets.json not found!${NC}"
        echo -e "  ${YELLOW}   Please copy secrets.json.template to secrets.json and add your bearer token.${NC}"
        echo -e "  ${YELLOW}   cp shared/config/secrets.json.template shared/config/secrets.json${NC}"
        echo -e "  ${YELLOW}   Then edit shared/config/secrets.json and replace YOUR_BEARER_TOKEN_HERE${NC}"
    else
        echo -e "  ✅ Secrets file found"
    fi
    
    if [ -f "$CONFIG_FILE" ]; then
        # Extract version info from config
        VERSION=$(jq -r '.version' "$CONFIG_FILE")
        VERSION_CODE=$(jq -r '.versionCode' "$CONFIG_FILE")
        API_URL=$(jq -r '.api.baseUrl' "$CONFIG_FILE")
        RSS_FEED_URL=$(jq -r '.rssFeed.url' "$CONFIG_FILE")

        # Update Android build.gradle.kts
        if [ -f "$ANDROID_DIR/app/build.gradle.kts" ]; then
            sed -i "s/versionName = \".*\"/versionName = \"$VERSION\"/" "$ANDROID_DIR/app/build.gradle.kts"
            sed -i "s/versionCode = .*/versionCode = $VERSION_CODE/" "$ANDROID_DIR/app/build.gradle.kts"
            sed -i "s|buildConfigField(\"String\", \"API_BASE_URL\", \"\\\".*\\\"\")|buildConfigField(\"String\", \"API_BASE_URL\", \"\\\"$API_URL\\\"\")|" "$ANDROID_DIR/app/build.gradle.kts"
            sed -i 's|buildConfigField("String", "RSS_FEED_URL", "\\"[^\\"]*\\"")|buildConfigField("String", "RSS_FEED_URL", "\\"'"$RSS_FEED_URL"'\\"")|' "$ANDROID_DIR/app/build.gradle.kts"
            echo -e "  ✅ Updated Android version to $VERSION ($VERSION_CODE)"
            echo -e "  ✅ Updated Android API URL to $API_URL"
            echo -e "  ✅ Updated Android RSS Feed URL to $RSS_FEED_URL"
        fi
        
        # Update iOS version - check for Info.plist first, otherwise skip with note
        if [ -f "$IOS_DIR/Artsorakel/Info.plist" ]; then
            # Try PlistBuddy first (macOS)
            if command -v /usr/libexec/PlistBuddy &> /dev/null; then
                /usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION" "$IOS_DIR/Artsorakel/Info.plist" 2>/dev/null
                /usr/libexec/PlistBuddy -c "Set :CFBundleVersion $VERSION_CODE" "$IOS_DIR/Artsorakel/Info.plist" 2>/dev/null
                echo -e "  ✅ Updated iOS version to $VERSION ($VERSION_CODE)"
            # Try plutil (macOS alternative)
            elif command -v plutil &> /dev/null; then
                plutil -replace CFBundleShortVersionString -string "$VERSION" "$IOS_DIR/Artsorakel/Info.plist"
                plutil -replace CFBundleVersion -string "$VERSION_CODE" "$IOS_DIR/Artsorakel/Info.plist"
                echo -e "  ✅ Updated iOS version to $VERSION ($VERSION_CODE)"
            # Fallback to manual XML editing for Linux
            else
                # Use sed to update the plist file
                sed -i "/<key>CFBundleShortVersionString<\/key>/!b;n;s/<string>.*<\/string>/<string>$VERSION<\/string>/" "$IOS_DIR/Artsorakel/Info.plist"
                sed -i "/<key>CFBundleVersion<\/key>/!b;n;s/<string>.*<\/string>/<string>$VERSION_CODE<\/string>/" "$IOS_DIR/Artsorakel/Info.plist"
                echo -e "  ✅ Updated iOS version to $VERSION ($VERSION_CODE) (using sed)"
            fi
        else
            echo -e "  ℹ️  iOS uses modern project configuration (no Info.plist) - version managed in Xcode"
        fi
    else
        echo -e "  ${RED}❌ Config file not found${NC}"
    fi
}

# Function to extract strings from Android XML to JSON format
extract_android_strings_to_json() {
    echo -e "${YELLOW}🔄 Extracting strings from Android to shared JSON format...${NC}"
    
    # Ensure shared strings directory exists
    mkdir -p "$SHARED_DIR/strings"
    
    # Language mapping: Android folder -> language code
    declare -A lang_map=(
        ["values"]="en"
        ["values-nb"]="nb"
        ["values-nn"]="nn"
        ["values-nl"]="nl"
    )
    
    for android_folder in "${!lang_map[@]}"; do
        lang_code="${lang_map[$android_folder]}"
        android_strings_file="$ANDROID_DIR/app/src/main/res/$android_folder/strings.xml"
        
        if [ -f "$android_strings_file" ]; then
            echo -e "  🔄 Processing $android_folder -> $lang_code"
            
            # Extract strings to JSON format
            shared_json_file="$SHARED_DIR/strings/$lang_code.json"
            
            # Use Python to parse XML and convert to JSON with proper escaping
            python3 << EOF
import xml.etree.ElementTree as ET
import json
import html

try:
    tree = ET.parse('$android_strings_file')
    root = tree.getroot()
    
    strings_dict = {}
    
    for string_elem in root.findall('.//string[@name]'):
        name = string_elem.get('name')
        value = string_elem.text or ''
        
        # Skip if it's marked as not translatable
        if string_elem.get('translatable') == 'false':
            continue
            
        # Handle XML entities and escape sequences
        # Android uses HTML entities, so we need to unescape them first
        value = html.unescape(value)
        
        strings_dict[name] = value
    
    # Write to JSON file with proper formatting
    with open('$shared_json_file', 'w', encoding='utf-8') as f:
        json.dump(strings_dict, f, ensure_ascii=False, indent=2, sort_keys=True)
    
    print(f"Successfully extracted {len(strings_dict)} strings")
    
except Exception as e:
    print(f"Error processing XML: {e}")
    exit(1)
EOF
            
            if [ $? -eq 0 ]; then
                echo -e "    ✅ Extracted strings to shared/$lang_code.json"
            else
                echo -e "    ❌ Failed to extract strings for $lang_code"
            fi
        else
            echo -e "    ⚠️  Android strings file not found: $android_strings_file"
        fi
    done
}

# Function to sync strings FROM shared CSV to platform-specific formats
sync_strings() {
    echo -e "${YELLOW}🌐 Syncing localization strings from shared CSV...${NC}"

    CSV_FILE="$SHARED_DIR/strings.csv"

    if [ ! -f "$CSV_FILE" ]; then
        echo -e "  ${RED}❌ CSV file not found at $CSV_FILE${NC}"
        return 1
    fi

    # Use Python to parse CSV and generate platform-specific files
    export SHARED_DIR ANDROID_DIR IOS_DIR
    python3 << 'PYTHON_EOF'
import csv
import xml.sax.saxutils as saxutils
import os

csv_file = os.environ['SHARED_DIR'] + '/strings.csv'
android_dir = os.environ['ANDROID_DIR']
ios_dir = os.environ['IOS_DIR']

# Read CSV
with open(csv_file, 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    rows = list(reader)

    if not rows:
        print("No data in CSV file")
        exit(1)

    # Get language codes from header (excluding 'key')
    languages = [col for col in reader.fieldnames if col != 'key']

    # Process each language
    for lang in languages:
        print(f"Processing language: {lang}")

        # Map language codes to Android resource folders
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
        android_strings_dir = f"{android_dir}/app/src/main/res/{android_lang}"
        os.makedirs(android_strings_dir, exist_ok=True)

        with open(f"{android_strings_dir}/strings.xml", 'w', encoding='utf-8') as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n')
            f.write('<resources>\n')
            f.write('    <!-- Auto-generated from shared CSV. Do not edit directly. -->\n')
            f.write('    <string name="app_name" translatable="false">Artsorakel</string>\n')

            for row in rows:
                key = row['key']
                value = row[lang]
                if value:  # Only write non-empty values
                    # Escape for Android XML
                    escaped_value = saxutils.escape(value)
                    escaped_value = escaped_value.replace("&apos;", "\\'")
                    escaped_value = escaped_value.replace("'", "\\'")
                    f.write(f'    <string name="{key}">{escaped_value}</string>\n')

            f.write('</resources>\n')

        print(f"  ✅ Generated Android strings for {lang}")

        # Generate iOS Localizable.strings
        ios_strings_dir = f"{ios_dir}/Artsorakel/{lang}.lproj"
        os.makedirs(ios_strings_dir, exist_ok=True)

        ios_resources_strings_dir = f"{ios_dir}/Artsorakel/Resources/Localizations/{lang}.lproj"
        os.makedirs(ios_resources_strings_dir, exist_ok=True)

        # Write to both locations
        for dir_path in [ios_strings_dir, ios_resources_strings_dir]:
            with open(f"{dir_path}/Localizable.strings", 'w', encoding='utf-8') as f:
                f.write('/* Auto-generated from shared CSV. Do not edit directly. */\n')

                for row in rows:
                    key = row['key']
                    value = row[lang]
                    if value:  # Only write non-empty values
                        # Escape for iOS .strings files
                        escaped_value = value.replace('"', '\\"').replace('\n', '\\n')
                        f.write(f'"{key}" = "{escaped_value}";\n')

        print(f"  ✅ Generated iOS strings for {lang}")

print(f"\nSuccessfully processed {len(languages)} languages from CSV")
PYTHON_EOF

    if [ $? -eq 0 ]; then
        echo -e "  ${GREEN}✅ Successfully synced strings from CSV${NC}"
    else
        echo -e "  ${RED}❌ Failed to sync strings from CSV${NC}"
        return 1
    fi
}

# Function to sync images
sync_images() {
    echo -e "${YELLOW}🖼️  Syncing images...${NC}"

    # Ensure Android assets directory exists
    mkdir -p "$ANDROID_DIR/app/src/main/assets"

    # Copy SVG logos to Android assets (exclude .inkscape.svg source files)
    if [ -d "$SHARED_DIR/images" ]; then
        for svg in "$SHARED_DIR/images"/*.svg; do
            if [ -f "$svg" ] && [[ ! "$svg" == *.inkscape.svg ]]; then
                cp "$svg" "$ANDROID_DIR/app/src/main/assets/"
                echo -e "  ✅ Copied $(basename "$svg") to Android assets"
                
                # For iOS, copy to Resources/Images directory
                mkdir -p "$IOS_DIR/Artsorakel/Artsorakel/Resources/Images"
                cp "$svg" "$IOS_DIR/Artsorakel/Artsorakel/Resources/Images/"
                echo -e "  ✅ Copied $(basename "$svg") to iOS Resources/Images"

            fi
        done
    fi

    # Copy vector SVGs from shared/vectors to iOS
    if [ -d "$SHARED_DIR/vectors" ]; then
        mkdir -p "$IOS_DIR/Artsorakel/Artsorakel/Resources/Vectors"
        for svg in "$SHARED_DIR/vectors"/*.svg; do
            if [ -f "$svg" ] && [[ ! "$svg" == *.inkscape.svg ]]; then
                # For iOS, copy to Resources/Vectors directory
                cp "$svg" "$IOS_DIR/Artsorakel/Artsorakel/Resources/Vectors/"
                echo -e "  ✅ Copied vector $(basename "$svg") to iOS Resources/Vectors"

            fi
        done
    fi
}

# Function to sync vector drawables
sync_vectors() {
    echo -e "${YELLOW}🎨 Syncing vector assets (SVG → Android VectorDrawable XML)...${NC}"

    mkdir -p "$ANDROID_DIR/app/src/main/res/drawable"
    mkdir -p "$ANDROID_DIR/app/src/main/res/drawable-night"

    # Function to check if SVG has transform attributes
    check_svg_for_transforms() {
        local svg_file="$1"
        local basename_file=$(basename "$svg_file")
        
        # Check for transform attributes in the SVG
        if grep -q 'transform=' "$svg_file"; then
            echo -e "${RED}❌ ERROR: SVG file '$basename_file' contains transform attributes!${NC}"
            echo -e "${YELLOW}   Transform attributes found:${NC}"
            grep 'transform=' "$svg_file" | head -5 | sed 's/^/     /'
            echo -e ""
            echo -e "${YELLOW}   The SVG file must be flattened before it can be converted to Android VectorDrawable.${NC}"
            echo -e "${YELLOW}   Please use Inkscape to:${NC}"
            echo -e "${YELLOW}     1. Open the file in Inkscape${NC}"
            echo -e "${YELLOW}     2. Select all (Ctrl+A)${NC}"
            echo -e "${YELLOW}     3. Ungroup multiple times until nothing is grouped${NC}"
            echo -e "${YELLOW}     4. Path → Object to Path${NC}"
            echo -e "${YELLOW}     5. Path → Stroke to Path${NC}"
            echo -e "${YELLOW}     6. Save as Optimized SVG with 10 decimal places${NC}"
            echo -e ""
            return 1
        fi
        return 0
    }

    # Convert avatar SVGs using vd-tool
    process_avatar_with_vdtool() {
        local input_file="$1"
        local output_file="$2"
        local basename_file=$(basename "$input_file")

        if [ ! -f "$input_file" ]; then
            echo -e "    ⚠️  File not found: $basename_file"
            return 1
        fi

        echo -e "  🔄 Processing $basename_file..."

        # Check for transforms first
        if ! check_svg_for_transforms "$input_file"; then
            echo -e "${RED}   ⛔ Aborting conversion of $basename_file${NC}"
            return 1
        fi

        if command -v vd-tool &> /dev/null; then
            # Convert with vd-tool
            vd-tool -c -in "$input_file" -out "$(dirname "$output_file")" 2>/dev/null

            # vd-tool creates file with same base name but .xml extension
            local expected_output="$(dirname "$output_file")/$(basename "$input_file" .svg).xml"
            local optimized_output="$(dirname "$output_file")/$(basename "$input_file" .optimized.svg).xml"

            # Check for both possible output names
            if [ -f "$optimized_output" ]; then
                mv "$optimized_output" "$output_file"
                echo -e "    ✅ Generated $(basename "$output_file")"
                return 0
            elif [ -f "$expected_output" ]; then
                mv "$expected_output" "$output_file"
                echo -e "    ✅ Generated $(basename "$output_file")"
                return 0
            else
                echo -e "    ❌ vd-tool conversion failed for $basename_file"
                return 1
            fi
        else
            echo -e "    ⚠️  vd-tool not found. Install with: npm install -g vd-tool"
            return 1
        fi
    }
    
    # Generate launcher icon drawables first
    echo -e "  🚀 Generating launcher icon drawables..."

    # Create launcher background ("dark subtle")
    cat > "$ANDROID_DIR/app/src/main/res/drawable/ic_launcher_background.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#394244"
        android:pathData="M0,0h108v108h-108z" />
</vector>
EOF
    echo -e "    ✅ Generated ic_launcher_background.xml"

    # Generate launcher foreground from logo
    if [ ! -f "$SHARED_DIR/images/ic_logo_color_dark.svg" ]; then
        echo -e "    ${RED}❌ ERROR: Logo SVG not found at $SHARED_DIR/images/ic_logo_color_dark.svg${NC}"
        echo -e "    ${RED}   Cannot generate launcher foreground without logo SVG${NC}"
        exit 1
    fi

    # Check if vd-tool is available
    if ! command -v vd-tool &> /dev/null; then
        echo -e "    ${RED}❌ ERROR: vd-tool not found but required for launcher icon conversion${NC}"
        echo -e "    ${RED}   Install with: npm install -g vd-tool${NC}"
        exit 1
    fi

    echo -e "    🔄 Converting logo to launcher foreground..."
    vd-tool -c -in "$SHARED_DIR/images/ic_logo_color_dark.svg" -out "$ANDROID_DIR/app/src/main/res/drawable/" 2>/dev/null

    # Check if conversion succeeded
    if [ ! -f "$ANDROID_DIR/app/src/main/res/drawable/ic_logo_color_dark.xml" ]; then
        echo -e "    ${RED}❌ ERROR: vd-tool conversion failed for launcher foreground${NC}"
        echo -e "    ${RED}   Unable to convert $SHARED_DIR/images/ic_logo_color_dark.svg${NC}"
        exit 1
    fi

    # Rename and adjust the generated file
    mv "$ANDROID_DIR/app/src/main/res/drawable/ic_logo_color_dark.xml" "$ANDROID_DIR/app/src/main/res/drawable/ic_launcher_foreground.xml"

    # Adjust the vector drawable to work as adaptive icon foreground
    sed -i '/<vector/,/<\/vector>/{
        s/android:width="[^"]*"/android:width="108dp"/
        s/android:height="[^"]*"/android:height="108dp"/
        s/android:viewportWidth="[^"]*"/android:viewportWidth="108"/
        s/android:viewportHeight="[^"]*"/android:viewportHeight="108"/
    }' "$ANDROID_DIR/app/src/main/res/drawable/ic_launcher_foreground.xml"

    echo -e "    ✅ Generated ic_launcher_foreground.xml from logo"

    # Process light/dark SVG pairs from images folder
    converted_any=false
    conversion_failed=false

    # Process all *_light.svg and *_dark.svg pairs
    if [ -d "$SHARED_DIR/images" ]; then
        # Find all unique base names for light/dark pairs
        for light_svg in "$SHARED_DIR/images"/*_light.svg "$SHARED_DIR/images"/*_light.optimized.svg; do
            [ -f "$light_svg" ] || continue

            # Extract base name (remove _light.svg or _light.optimized.svg)
            basename_full=$(basename "$light_svg")
            if [[ "$basename_full" == *_light.optimized.svg ]]; then
                base_name="${basename_full%_light.optimized.svg}"
                is_optimized=true
            else
                base_name="${basename_full%_light.svg}"
                is_optimized=false
            fi

            # Convert to Android resource name (lowercase with underscores)
            # If base_name already starts with ic_, don't add another ic_ prefix
            base_name_lower=$(echo "$base_name" | tr '[:upper:]' '[:lower:]' | sed 's/-/_/g')
            if [[ "$base_name_lower" == ic_* ]]; then
                android_name="$base_name_lower"
            else
                android_name="ic_${base_name_lower}"
            fi

            # Process light version
            if process_avatar_with_vdtool "$light_svg" "$ANDROID_DIR/app/src/main/res/drawable/${android_name}.xml"; then
                converted_any=true

                # Look for corresponding dark version
                if [ "$is_optimized" = true ]; then
                    dark_svg="$SHARED_DIR/images/${base_name}_dark.optimized.svg"
                    if [ ! -f "$dark_svg" ]; then
                        dark_svg="$SHARED_DIR/images/${base_name}_dark.svg"
                    fi
                else
                    dark_svg="$SHARED_DIR/images/${base_name}_dark.svg"
                fi

                # Process dark version if it exists
                if [ -f "$dark_svg" ]; then
                    if process_avatar_with_vdtool "$dark_svg" "$ANDROID_DIR/app/src/main/res/drawable-night/${android_name}.xml"; then
                        echo -e "    ✅ Generated light/dark pair for ${android_name}"
                    else
                        conversion_failed=true
                    fi
                else
                    echo -e "    ⚠️  No dark variant found for ${base_name}"
                fi
            else
                conversion_failed=true
            fi
        done
    fi

    if [ "$conversion_failed" = true ]; then
        echo -e "${RED}⛔ Some conversions failed due to transform attributes. Please fix the SVG files first.${NC}"
        return 1
    fi

    if [ "$converted_any" = false ]; then
        echo -e "  ⚠️  No light/dark SVG pairs found to convert in shared/images/"
    fi

    # Process other vector files with vd-tool
    if [ -d "$SHARED_DIR/vectors" ]; then
        for svg in "$SHARED_DIR/vectors"/*.svg; do
            [ -f "$svg" ] || continue
            name=$(basename "$svg" .svg)
            # Replace Norwegian characters with ASCII equivalents for Android drawable names
            name=$(echo "$name" | sed 's/æ/ae/g; s/ø/oe/g; s/å/aa/g; s/Æ/Ae/g; s/Ø/Oe/g; s/Å/Aa/g')
            out="$ANDROID_DIR/app/src/main/res/drawable/${name}.xml"

            echo -e "  🔄 Converting $(basename "$svg") → $(basename "$out")"

            # Check for transforms first
            if ! check_svg_for_transforms "$svg"; then
                echo -e "${RED}   ⛔ Skipping conversion of $(basename "$svg")${NC}"
                continue
            fi

            if command -v vd-tool &> /dev/null; then
                # Convert with vd-tool
                vd-tool -c -in "$svg" -out "$ANDROID_DIR/app/src/main/res/drawable/" 2>/dev/null

                # vd-tool creates file with same base name but .xml extension
                # Need to rename if original name contains Norwegian characters
                original_name=$(basename "$svg" .svg)
                generated_file="$ANDROID_DIR/app/src/main/res/drawable/${original_name}.xml"

                if [ -f "$generated_file" ] && [ "$generated_file" != "$out" ]; then
                    # File was generated but needs renaming due to Norwegian characters
                    mv "$generated_file" "$out"
                    echo -e "    ✅ Generated and renamed $(basename "$out")"
                elif [ -f "$out" ]; then
                    echo -e "    ✅ Generated $(basename "$out")"
                else
                    echo -e "    ❌ Failed to convert $(basename "$svg")"
                fi
            else
                echo -e "    ⚠️ vd-tool not found. Install with: npm install -g vd-tool"
            fi
        done
    fi
}

# Function to sync fonts
sync_fonts() {
    echo -e "${YELLOW}🔤 Syncing fonts...${NC}"

    # Ensure Android font directory exists
    mkdir -p "$ANDROID_DIR/app/src/main/res/font"

    if [ -d "$SHARED_DIR/fonts" ]; then
        # Copy fonts to Android
        for font in "$SHARED_DIR/fonts"/*.ttf; do
            if [ -f "$font" ]; then
                cp "$font" "$ANDROID_DIR/app/src/main/res/font/"
                echo -e "  ✅ Copied $(basename "$font") to Android fonts"
            fi
        done
        
        # Copy fonts to iOS Resources directory
        mkdir -p "$IOS_DIR/Artsorakel/Artsorakel/Resources/Fonts"
        for font in "$SHARED_DIR/fonts"/*.ttf; do
            if [ -f "$font" ]; then
                # Copy to Resources/Fonts directory
                cp "$font" "$IOS_DIR/Artsorakel/Artsorakel/Resources/Fonts/"
                echo -e "  ✅ Copied $(basename "$font") to iOS Resources/Fonts"
            fi
        done
    fi
}

# Function to sync HTML content
sync_content() {
    echo -e "${YELLOW}📄 Syncing HTML content...${NC}"

    # Ensure Android assets directory exists
    mkdir -p "$ANDROID_DIR/app/src/main/assets"

    if [ -d "$SHARED_DIR/content" ]; then
        # Copy FAQ JSON files directly to Android assets
        for json in "$SHARED_DIR/content"/faq_*.json; do
            if [ -f "$json" ]; then
                filename=$(basename "$json")
                cp "$json" "$ANDROID_DIR/app/src/main/assets/"
                echo -e "  ✅ Copied $filename to Android assets"
            fi
        done
        
        # Copy other HTML files to Android assets (non-FAQ)
        for html in "$SHARED_DIR/content"/*.html; do
            if [ -f "$html" ]; then
                filename=$(basename "$html")
                # Skip FAQ files as they're converted to JSON
                if [[ ! "$filename" =~ ^faq_ ]]; then
                    cp "$html" "$ANDROID_DIR/app/src/main/assets/"
                    echo -e "  ✅ Copied $(basename "$html") to Android assets"
                fi
            fi
        done
        
        # Copy HTML files to iOS resources (non-FAQ)
        mkdir -p "$IOS_DIR/Artsorakel/Resources/Content"
        for html in "$SHARED_DIR/content"/*.html; do
            if [ -f "$html" ]; then
                filename=$(basename "$html")
                # Skip FAQ files as they're handled as JSON
                if [[ ! "$filename" =~ ^faq_ ]]; then
                    cp "$html" "$IOS_DIR/Artsorakel/Resources/Content/"
                    echo -e "  ✅ Copied $(basename "$html") to iOS resources"
                fi
            fi
        done
        
        # Copy FAQ JSON files to iOS resources
        for json in "$SHARED_DIR/content"/faq_*.json; do
            if [ -f "$json" ]; then
                filename=$(basename "$json")
                cp "$json" "$IOS_DIR/Artsorakel/Resources/Content/"
                echo -e "  ✅ Copied $filename to iOS resources"
            fi
        done
    fi
}

# Function to sync design system
sync_design_system() {
    echo -e "${YELLOW}🎨 Syncing design system colors and themes...${NC}"

    DESIGN_DIR="$SHARED_DIR/designsystem"

    if [ ! -d "$DESIGN_DIR" ]; then
        echo -e "  ⚠️  Design system directory not found"
        return 0
    fi

    # Process Variables primitives.txt to generate color primitives
    if [ -f "$DESIGN_DIR/Variables primitives.txt" ]; then
        echo -e "  🔄 Processing Variables primitives.txt..."

        python3 << 'EOF'
import os
import re

design_dir = os.environ['SHARED_DIR'] + '/designsystem'
android_dir = os.environ['ANDROID_DIR']

# Read the Variables primitives file
with open(f"{design_dir}/Variables primitives.txt", 'r') as f:
    content = f.read()

# Extract color variables
color_map = {}
pattern = r'--([a-z-]+(?:-\d+|-base-positive|-base-negative|-base)?)\s*:\s*(#[A-F0-9]{6})'
matches = re.findall(pattern, content, re.IGNORECASE)

for name, value in matches:
    # Convert CSS var name to Android resource name
    android_name = name.replace('-', '_')
    color_map[android_name] = value.upper()

# Read Variables listcategories.txt if it exists
if os.path.exists(f"{design_dir}/Variables listcategories.txt"):
    with open(f"{design_dir}/Variables listcategories.txt", 'r') as f:
        listcat_content = f.read()

    # Extract invasive and redlist category colors
    listcat_pattern = r'--((?:invasive|redlist)-[a-z]+)\s*:\s*(#[A-F0-9]{6})'
    listcat_matches = re.findall(listcat_pattern, listcat_content, re.IGNORECASE)

    for name, value in listcat_matches:
        # Convert CSS var name to Android resource name
        android_name = name.replace('-', '_')
        color_map[android_name] = value.upper()

    print(f"  ✅ Found {len(listcat_matches)} list category colors")

# Read existing colors.xml from designsystem
existing_colors = {}
if os.path.exists(f"{design_dir}/colors.xml"):
    with open(f"{design_dir}/colors.xml", 'r') as f:
        colors_content = f.read()
        # Extract existing colors
        color_pattern = r'<color name="([^"]+)">([^<]+)</color>'
        for match in re.findall(color_pattern, colors_content):
            existing_colors[match[0]] = match[1]

# Read functional colors
if os.path.exists(f"{design_dir}/functional_colors.xml"):
    with open(f"{design_dir}/functional_colors.xml", 'r') as f:
        func_content = f.read()
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
os.makedirs(f"{android_dir}/app/src/main/res/values", exist_ok=True)
with open(f"{android_dir}/app/src/main/res/values/colors.xml", 'w') as f:
    f.write(colors_xml)

print(f"  ✅ Generated colors.xml with {len(all_colors)} colors")
EOF
    fi

    # Process Semantic tokens.txt to add semantic color references and generate themes
    if [ -f "$DESIGN_DIR/Semantic tokens.txt" ] && [ -f "$DESIGN_DIR/Variables primitives.txt" ]; then
        echo -e "  🔄 Processing Semantic tokens and generating themes..."

        python3 << 'EOF'
import os
import re
import xml.etree.ElementTree as ET

design_dir = os.environ['SHARED_DIR'] + '/designsystem'
android_dir = os.environ['ANDROID_DIR']

# First, read all primitive colors from Variables primitives.txt
primitives = {}
with open(f"{design_dir}/Variables primitives.txt", 'r') as f:
    content = f.read()
    pattern = r'--([a-z-]+(?:-\d+|-base-positive|-base-negative|-base)?)\s*:\s*(#[A-F0-9]{6})'
    for match in re.findall(pattern, content, re.IGNORECASE):
        name = match[0].replace('-', '_')
        primitives[name] = match[1].upper()

# Read the Semantic tokens file
with open(f"{design_dir}/Semantic tokens.txt", 'r') as f:
    content = f.read()

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
app_attrs_file = f"{design_dir}/app_attrs.xml"
if os.path.exists(app_attrs_file):
    tree = ET.parse(app_attrs_file)
    root = tree.getroot()
    for styleable in root.findall('.//declare-styleable'):
        attrs_xml += f'\n    <!-- {styleable.get("name")} attributes -->\n'
        attrs_xml += '    ' + ET.tostring(styleable, encoding='unicode').strip() + '\n'

attrs_xml += '</resources>\n'

# Write attrs.xml
os.makedirs(f"{android_dir}/app/src/main/res/values", exist_ok=True)
with open(f"{android_dir}/app/src/main/res/values/attrs.xml", 'w') as f:
    f.write(attrs_xml)

print(f"  ✅ Generated attrs.xml with {len(all_token_names) + len(rss_attrs)} attributes")

# Now generate theme files with proper mappings
def resolve_color_reference(token_value, primitives, light_tokens, dark_tokens):
    """Resolve a color reference to an actual color resource"""
    # Handle opacity as a float value
    if token_value == "0.3":
        return None  # Skip opacity - it's not a color

    # If it references another token, resolve that first
    if token_value in light_tokens or token_value in dark_tokens:
        # This is a reference to another semantic token, use attr reference
        return f"?attr/{token_value}"

    # If it's a primitive color
    if token_value in primitives:
        return f"@color/{token_value}"

    # Handle special cases
    special_mappings = {
        "hav": "@color/ocean",
        "white": "@color/white",
        "ocean": "@color/ocean"
    }

    if token_value in special_mappings:
        return special_mappings[token_value]

    # Handle missing alert/functional colors
    missing_color_mappings = {
        "red_base": "@color/red_base",
        "green_base": "@color/green_base",
        "green_10": "@color/green_10",
        "green_50": "@color/green_50",
        "green_90": "@color/green_90",
        "green_100": "@color/green_100",
        "blue_base": "@color/blue_base",
        "blue_20": "@color/blue_20",
        "blue_50": "@color/blue_50",
        "blue_80": "@color/blue_80",
        "blue_90": "@color/blue_90",
        "yellow_base": "@color/yellow_base",
        "yellow_20": "@color/yellow_20",
        "yellow_80": "@color/yellow_80",
        "yellow_90": "@color/yellow_90"
    }

    if token_value in missing_color_mappings:
        return missing_color_mappings[token_value]

    # If it's a direct color value
    if token_value.startswith('#'):
        return token_value
    else:
        # Default to trying as a color reference
        return f"@color/{token_value}"

# Read the shared theme file
shared_theme_file = f"{design_dir}/themes_shared.xml"

# Generate light theme
if os.path.exists(shared_theme_file):
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
    tree.write(f"{android_dir}/app/src/main/res/values/themes.xml", encoding='utf-8', xml_declaration=True)
    print(f"  ✅ Generated light theme with semantic tokens")

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
    os.makedirs(f"{android_dir}/app/src/main/res/values-night", exist_ok=True)
    dark_tree.write(f"{android_dir}/app/src/main/res/values-night/themes.xml", encoding='utf-8', xml_declaration=True)
    print(f"  ✅ Generated dark theme with semantic tokens")
else:
    print(f"  ⚠️  themes_shared.xml not found in {design_dir}")

EOF
    fi

    # Generate iOS color assets from design system
    echo -e "  🔄 Generating iOS color assets..."

    python3 << 'EOF'
import os
import re
import json

design_dir = os.environ['SHARED_DIR'] + '/designsystem'
ios_dir = os.environ['IOS_DIR']

# Read primitives
primitives = {}
with open(f"{design_dir}/Variables primitives.txt", 'r') as f:
    content = f.read()
    pattern = r'--([a-z-]+(?:-\d+|-base-positive|-base-negative|-base)?)\s*:\s*(#[A-F0-9]{6})'
    for match in re.findall(pattern, content, re.IGNORECASE):
        name = match[0].replace('-', '_')
        primitives[name] = match[1].upper()

# Read semantic tokens for light and dark
light_tokens = {}
dark_tokens = {}

with open(f"{design_dir}/Semantic tokens.txt", 'r') as f:
    content = f.read()

# Parse light mode tokens
light_section = re.search(r':root\s*{([^}]+)}', content, re.DOTALL)
if light_section:
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

def hex_to_rgb(hex_color):
    """Convert hex color to RGB components (0-1 range)"""
    hex_color = hex_color.lstrip('#')
    r, g, b = tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4))
    return r/255.0, g/255.0, b/255.0

def resolve_color(token_value, primitives):
    """Resolve a token to actual hex color"""
    if token_value.startswith('#'):
        return token_value
    elif token_value in primitives:
        return primitives[token_value]
    elif token_value == 'white':
        return '#FFFFFF'
    elif token_value == 'hav' or token_value == 'ocean':
        return '#262F31'
    else:
        # Try to find it in primitives with underscore version
        underscored = token_value.replace('-', '_')
        if underscored in primitives:
            return primitives[underscored]
    return None

# Key semantic tokens for iOS - prefixed with Color_ to avoid conflicts
ios_colors = {
    'Color_backgroundDefault': ('background_default', 'background_default'),
    'Color_backgroundSubtle': ('background_subtle', 'background_subtle'),
    'Color_surfacePrimary': ('surface_primary', 'surface_primary'),
    'Color_surfaceAccent': ('surface_accent', 'surface_accent'),
    'Color_textPrimary': ('text_primary', 'text_primary'),
    'Color_textAccent': ('text_accent', 'text_accent'),
    'Color_borderDefault': ('border_default', 'border_default')
}

# Create color assets
assets_dir = f"{ios_dir}/Artsorakel/Assets.xcassets"
os.makedirs(assets_dir, exist_ok=True)

for ios_name, (light_token, dark_token) in ios_colors.items():
    colorset_dir = f"{assets_dir}/{ios_name}.colorset"
    os.makedirs(colorset_dir, exist_ok=True)

    # Get actual colors
    light_ref = light_tokens.get(light_token)
    dark_ref = dark_tokens.get(dark_token)

    light_color = resolve_color(light_ref, primitives) if light_ref else None
    dark_color = resolve_color(dark_ref, primitives) if dark_ref else None

    if light_color and dark_color:
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

        with open(f"{colorset_dir}/Contents.json", 'w') as f:
            json.dump(contents, f, indent=2)

        print(f"  ✅ Generated {ios_name}.colorset")

print(f"  ✅ Generated iOS color assets from design system")
EOF

}

# Function to sync app icons
sync_app_icons() {
    echo -e "${YELLOW}🎨 Syncing app icons...${NC}"

    # This would require icon generation from a base icon
    # For now, just create placeholder
    if [ -f "$SHARED_DIR/images/app_icon.svg" ] || [ -f "$SHARED_DIR/images/app_icon.png" ]; then
        echo -e "  ℹ️  App icon sync would be implemented here"
        echo -e "  ℹ️  Use tools like ImageMagick to generate required sizes"
    else
        echo -e "  ⚠️  No app icon found in shared/images/"
    fi
}

# Function to validate sync
validate_sync() {
    echo -e "${YELLOW}✔️  Validating sync...${NC}"
    
    # Check if critical files exist
    errors=0
    
    # Check Android
    if [ ! -f "$ANDROID_DIR/app/build.gradle.kts" ]; then
        echo -e "  ${RED}❌ Android build.gradle.kts not found${NC}"
        ((errors++))
    else
        echo -e "  ✅ Android project structure valid"
    fi
    
    # Check iOS - look for modern project structure
    if [ ! -f "$IOS_DIR/Artsorakel.xcodeproj/project.pbxproj" ]; then
        echo -e "  ${RED}❌ iOS project.pbxproj not found${NC}"
        ((errors++))
    elif [ ! -d "$IOS_DIR/Artsorakel" ]; then
        echo -e "  ${RED}❌ iOS app target directory not found${NC}"
        ((errors++))
    else
        echo -e "  ✅ iOS project structure valid"
    fi
    
    if [ $errors -eq 0 ]; then
        echo -e "  ${GREEN}✅ Validation passed${NC}"
    else
        echo -e "  ${RED}❌ Validation failed with $errors errors${NC}"
        return 1
    fi
}

# Main execution
main() {
    # Check dependencies
    if ! command -v jq &> /dev/null; then
        echo -e "${RED}❌ jq is required but not installed. Install it with: apt-get install jq (Linux) or brew install jq (macOS)${NC}"
        exit 1
    fi
    
    # Run sync functions
    sync_config
    sync_strings
    sync_images
    sync_vectors
    sync_fonts
    sync_content
    sync_design_system
    sync_app_icons
    
    # Validate
    if validate_sync; then
        echo -e "${GREEN}✨ Resource synchronization completed successfully!${NC}"
    else
        echo -e "${YELLOW}⚠️  Synchronization completed with warnings${NC}"
    fi
}

# Run main function
main "$@"