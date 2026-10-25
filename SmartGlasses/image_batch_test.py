from ultralytics import YOLO
import glob
import os

# Load the pretrained YOLO11n model
model = YOLO("yolo11n.pt")

# Get all JPG and JPEG images
image_files = glob.glob("test_images/*.jpg")
image_files += glob.glob("test_images/*.jpeg")
image_files += glob.glob("test_images/*.png")

print("================================")
print("YOLO IMAGE TESTING")
print("================================")

print(f"Images found: {len(image_files)}")
print()

# Process every image
for image_path in image_files:

    print("--------------------------------")
    print(f"Testing: {os.path.basename(image_path)}")
    print("--------------------------------")

    results = model(image_path, save=True)

    detected_objects = set()

    for result in results:
        for box in result.boxes:

            class_id = int(box.cls[0])
            confidence = float(box.conf[0])
            class_name = model.names[class_id]

            detected_objects.add(class_name)

            print(
                f"  {class_name} | "
                f"Confidence: {confidence:.2f}"
            )

    if not detected_objects:
        print("  No objects detected.")

    print()

print("================================")
print("IMAGE TESTING COMPLETED!")
print("================================")