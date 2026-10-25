from ultralytics import YOLO


# ---------------------------------------
# Risk calculation
# ---------------------------------------

def calculate_risk(distance, moving_toward):
    
    if distance < 1.5:
        return "DANGER"

    elif distance <= 3.0:
        return "WARNING"

    else:
        if moving_toward:
            return "WARNING"
        else:
            return "SAFE"


# ---------------------------------------
# Direction calculation
# ---------------------------------------

def calculate_direction(x_center, image_width):

    if x_center < image_width / 3:
        return "LEFT"

    elif x_center > (image_width * 2) / 3:
        return "RIGHT"

    else:
        return "CENTER"


# ---------------------------------------
# Load YOLO model
# ---------------------------------------

model = YOLO("yolo11n.pt")


# ---------------------------------------
# Image to test
# ---------------------------------------

image_path = "test_images/test.jpg"


# ---------------------------------------
# Simulated distance
# ---------------------------------------

distance = 2.0


# ---------------------------------------
# Simulated movement
# ---------------------------------------

moving_toward = True


# ---------------------------------------
# Run YOLO
# ---------------------------------------

results = model.predict(
    source=image_path,
    conf=0.50,
    imgsz=640,
    save=True
)


print("================================")
print("COMBINED AI DECISION SYSTEM")
print("================================")


# ---------------------------------------
# Process detected objects
# ---------------------------------------

for result in results:

    image_width = result.orig_shape[1]

    for box in result.boxes:

        class_id = int(box.cls[0])
        confidence = float(box.conf[0])
        class_name = model.names[class_id]

        # Bounding box coordinates
        x1, y1, x2, y2 = box.xyxy[0].tolist()

        # Calculate center of object
        x_center = (x1 + x2) / 2

        # Calculate direction
        direction = calculate_direction(
            x_center,
            image_width
        )

        # Calculate risk
        risk = calculate_risk(
            distance,
            moving_toward
        )

        print("--------------------------------")
        print("Object      :", class_name)
        print("Confidence  :", round(confidence, 2))
        print("Distance    :", distance, "meters")
        print("Direction   :", direction)
        print("Moving      :", moving_toward)
        print("Risk        :", risk)
        print("--------------------------------")


print("================================")
print("COMBINED DETECTION COMPLETED")
print("================================")