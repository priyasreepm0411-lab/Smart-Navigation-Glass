from ultralytics import YOLO

# Load the pretrained YOLO11n model
model = YOLO("yolo11n.pt")

# Run YOLO on the test image
results = model("test_images/test.jpg", save=True)

print("================================")
print("PERSON DETECTION")
print("================================")

person_found = False

for result in results:
    for box in result.boxes:

        class_id = int(box.cls[0])
        confidence = float(box.conf[0])
        class_name = model.names[class_id]

        # Check only for person
        if class_name == "person":
            person_found = True

            print(
                f"Person detected | "
                f"Confidence: {confidence:.2f}"
            )

if not person_found:
    print("No person detected.")

print("================================")
print("Person detection completed!")
print("================================")