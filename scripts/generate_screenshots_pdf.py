import os
from PIL import Image

def generate_pdf():
    screenshots_dir = os.path.join("docs", "android", "screenshots")
    output_pdf = os.path.join(screenshots_dir, "Savyor_Android_UI_Screenshots.pdf")

    # Ordered list of screenshots to include
    images_to_include = [
        "manage_categories_light.png",
        "manage_categories_dark.png",
        "detail_my_note_light.png",
        "detail_my_note_dark.png",
        "detail_my_note_editing_light.png",
        "settings_backup_light.png",
        "settings_backup_dark.png",
        "settings_backup_scrolled_light.png",
        "library_categories_light.png",
        "library_grid_light.png",
        "library_grid_dark.png",
        "detail_fullscreen_lightbox.png"
    ]

    pil_images = []
    for img_name in images_to_include:
        img_path = os.path.join(screenshots_dir, img_name)
        if os.path.exists(img_path):
            img = Image.open(img_path).convert("RGB")
            pil_images.append(img)
            print(f"Added to PDF: {img_name}")
        else:
            print(f"Skipping missing: {img_name}")

    if pil_images:
        first_img = pil_images[0]
        first_img.save(
            output_pdf,
            "PDF",
            resolution=100.0,
            save_all=True,
            append_images=pil_images[1:]
        )
        print(f"\nSuccessfully generated {output_pdf} ({len(pil_images)} pages, {os.path.getsize(output_pdf)} bytes)")

if __name__ == "__main__":
    generate_pdf()
