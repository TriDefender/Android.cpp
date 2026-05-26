#!/usr/bin/env python3
from PIL import Image
import os

# Source icon
source_path = "icon.png"
res_path = "app/src/main/res"

# Define icon sizes for different densities
# Standard launcher icons
launcher_sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Round launcher icons (same sizes)
round_sizes = launcher_sizes

# Open the source image
img = Image.open(source_path)

# Ensure the image is square
if img.width != img.height:
    print(f"Warning: Image is not square ({img.width}x{img.height}), cropping to square")
    size = min(img.width, img.height)
    left = (img.width - size) // 2
    top = (img.height - size) // 2
    img = img.crop((left, top, left + size, top + size))

# Create launcher icons
print("Creating launcher icons...")
for density, size in launcher_sizes.items():
    dir_path = os.path.join(res_path, density)
    os.makedirs(dir_path, exist_ok=True)

    # Remove existing icon files
    icon_path = os.path.join(dir_path, "ic_launcher.png")
    if os.path.exists(icon_path):
        os.remove(icon_path)

    # Remove existing XML files
    xml_path = os.path.join(dir_path, "ic_launcher.xml")
    if os.path.exists(xml_path):
        os.remove(xml_path)

    # Resize and save
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(icon_path, "PNG")
    print(f"  Created {icon_path} ({size}x{size})")

    # Remove round icon XML
    round_xml_path = os.path.join(dir_path, "ic_launcher_round.xml")
    if os.path.exists(round_xml_path):
        os.remove(round_xml_path)

    # Create round icon (same as regular)
    round_icon_path = os.path.join(dir_path, "ic_launcher_round.png")
    if os.path.exists(round_icon_path):
        os.remove(round_icon_path)
    resized.save(round_icon_path, "PNG")
    print(f"  Created {round_icon_path} ({size}x{size})")

print("\nDone! Icons created successfully.")
