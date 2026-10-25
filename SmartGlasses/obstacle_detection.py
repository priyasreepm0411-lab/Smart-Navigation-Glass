from ultralytics import YOLO

# Load the pretrained YOLO11n model
model = YOLO("yolo11n.pt")

# Run YOLO on the test image
results = model("test_images/test.jpg", save=True)

print("================================")
print("COMMON OBSTACLE DETECTION")
print("================================")

# Objects that can be treated as obstacles
obstacle_classes = [
    "person",
    "bicycle",
    "motorcycle",
    "car",
    "bus",
    "truck",
    "traffic light",
    "stop sign"
]

obstacle_found = False

for result in results:
    for box in result.boxes:

        class_id = int(box.cls[0])
        confidence = float(box.conf[0])
        class_name = model.names[class_id]

        if class_name in obstacle_classes:
            obstacle_found = True

            print(
                f"Obstacle detected: {class_name} | "
                f"Confidence: {confidence:.2f}"
            )

if not obstacle_found:
    print("No common obstacle detected.")

print("================================")
print("Obstacle detection completed!")
print("================================")