from ultralytics import YOLO

# Load the pretrained YOLO11n model
model = YOLO("yolo11n.pt")

# Run YOLO on the test image
results = model("test_images/test.jpg", save=True)

print("================================")
print("YOLO IMAGE DETECTION")
print("================================")

# Display detected objects
for result in results:
    for box in result.boxes:
        class_id = int(box.cls[0])
        confidence = float(box.conf[0])

        class_name = model.names[class_id]

        print(
            f"Detected: {class_name} | "
            f"Confidence: {confidence:.2f}"
        )

print("================================")
print("Detection completed!")
print("================================")