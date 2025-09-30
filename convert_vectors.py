#!/usr/bin/env python3
"""
Convert Android Vector Drawables to iOS-compatible formats (SF Symbols JSON or SVG)
"""

import xml.etree.ElementTree as ET
import json
import re
import os
import sys
from pathlib import Path

def parse_android_color(color_str):
    """Convert Android color format to hex"""
    if color_str.startswith('#'):
        return color_str
    # Handle @color references - would need color resource file
    return '#000000'

def android_vector_to_svg(xml_file):
    """Convert Android vector drawable XML to SVG"""
    tree = ET.parse(xml_file)
    root = tree.getroot()
    
    # Get viewport dimensions
    width = root.get('{http://schemas.android.com/apk/res/android}width', '24dp')
    height = root.get('{http://schemas.android.com/apk/res/android}height', '24dp')
    viewport_width = root.get('{http://schemas.android.com/apk/res/android}viewportWidth', '24')
    viewport_height = root.get('{http://schemas.android.com/apk/res/android}viewportHeight', '24')
    
    # Remove 'dp' suffix if present
    width = width.replace('dp', '')
    height = height.replace('dp', '')
    
    # Create SVG root
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {viewport_width} {viewport_height}">\n'
    
    # Process paths
    for path in root.findall('.//path'):
        path_data = path.get('{http://schemas.android.com/apk/res/android}pathData', '')
        fill_color = path.get('{http://schemas.android.com/apk/res/android}fillColor', '#000000')
        stroke_color = path.get('{http://schemas.android.com/apk/res/android}strokeColor')
        stroke_width = path.get('{http://schemas.android.com/apk/res/android}strokeWidth', '0')
        
        svg += f'  <path d="{path_data}"'
        if fill_color:
            svg += f' fill="{parse_android_color(fill_color)}"'
        if stroke_color:
            svg += f' stroke="{parse_android_color(stroke_color)}"'
        if stroke_width and stroke_width != '0':
            svg += f' stroke-width="{stroke_width}"'
        svg += '/>\n'
    
    svg += '</svg>'
    return svg

def create_ios_symbol_config(name, svg_content):
    """Create iOS symbol configuration for use with SF Symbols or custom symbols"""
    return {
        "name": name,
        "type": "custom",
        "svg": svg_content,
        "renderingMode": "template",  # Allow tinting
        "scales": ["1x", "2x", "3x"],
        "appearances": ["light", "dark"]
    }

def process_vector_files(input_dir, output_dir):
    """Process all Android vector files and convert them"""
    input_path = Path(input_dir)
    output_path = Path(output_dir)
    
    # Create output directories
    svg_dir = output_path / "svg"
    json_dir = output_path / "ios_symbols"
    svg_dir.mkdir(parents=True, exist_ok=True)
    json_dir.mkdir(parents=True, exist_ok=True)
    
    results = []
    
    for xml_file in input_path.glob("*.xml"):
        try:
            # Skip non-vector files (like shape drawables)
            with open(xml_file, 'r') as f:
                content = f.read()
                if '<vector' not in content:
                    print(f"Skipping non-vector file: {xml_file.name}")
                    continue
            
            # Convert to SVG
            svg_content = android_vector_to_svg(xml_file)
            
            # Save SVG file
            svg_filename = xml_file.stem + ".svg"
            svg_path = svg_dir / svg_filename
            with open(svg_path, 'w') as f:
                f.write(svg_content)
            
            # Create iOS symbol config
            symbol_config = create_ios_symbol_config(xml_file.stem, svg_content)
            json_path = json_dir / (xml_file.stem + ".json")
            with open(json_path, 'w') as f:
                json.dump(symbol_config, f, indent=2)
            
            results.append({
                "name": xml_file.stem,
                "svg": str(svg_path),
                "ios_config": str(json_path)
            })
            
            print(f"✅ Converted: {xml_file.name}")
            
        except Exception as e:
            print(f"❌ Error converting {xml_file.name}: {e}")
    
    # Create summary file
    summary_path = output_path / "conversion_summary.json"
    with open(summary_path, 'w') as f:
        json.dump({
            "total_files": len(results),
            "converted": results
        }, f, indent=2)
    
    print(f"\n✨ Conversion complete! Processed {len(results)} files")
    print(f"SVG files: {svg_dir}")
    print(f"iOS configs: {json_dir}")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        input_directory = sys.argv[1]
        output_directory = sys.argv[2] if len(sys.argv) > 2 else "./converted"
    else:
        # Default to current directory
        input_directory = "."
        output_directory = "./converted"
    
    process_vector_files(input_directory, output_directory)