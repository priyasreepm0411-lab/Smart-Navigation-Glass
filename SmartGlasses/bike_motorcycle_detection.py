from ultralytics import YOLO

# Load the pretrained YOLO11n model
model = YOLO("yolo11n.pt")

# Run YOLO on the test image
results = model("test_images/test.jpg", save=True)

print("================================")
print("BICYCLE & MOTORCYCLE DETECTION")
print("================================")

vehicle_found = False

# Objects we want to detect
target_classes = ["bicycle", "motorcycle"]

for result in results:
    for box in result.boxes:

        class_id = int(box.cls[0])
        confidence = float(box.conf[0])
        class_name = model.names[class_id]

        # Check for bicycle or motorcycle
        if class_name in target_classes:
            vehicle_found = True

            print(
                f"Detected: {class_name} | "
                f"Confidence: {confidence:.2f}"
            )

if not vehicle_found:
    print("No bicycle or motorcycle detected.")

print("================================")
print("Detection completed!")
print("================================")