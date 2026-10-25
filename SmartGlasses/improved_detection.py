from ultralytics import YOLO

# Load YOLO11n model
model = YOLO("yolo11n.pt")

# Image to test
image_path = "test_images/test.jpg"

# Run detection
results = model.predict(
    source=image_path,
    conf=0.50,
    imgsz=640,
    save=True
)

print("================================")
print("IMPROVED YOLO DETECTION")
print("================================")

object_found = False

for result in results:
    for box in result.boxes:

        class_id = int(box.cls[0])
        confidence = float(box.conf[0])
        class_name = model.names[class_id]

        print(
            f"Object: {class_name} | "
            f"Confidence: {confidence:.2f}"
        )

        object_found = True

if not object_found:
    print("No objects detected.")

print("================================")
print("Detection completed!")
print("Confidence threshold: 0.50")
print("Image size: 640")
print("================================")