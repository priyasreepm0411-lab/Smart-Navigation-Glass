from ultralytics import YOLO


# ---------------------------------------
# Calculate risk
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
# Create voice/message output
# ---------------------------------------

def create_message(object_name, distance, direction, risk):

    if risk == "DANGER":
        return (
            f"Danger! {object_name} very close "
            f"on the {direction.lower()}."
        )

    elif risk == "WARNING":
        return (
            f"Warning! {object_name} ahead "
            f"on the {direction.lower()}."
        )

    else:
        return (
            f"Safe. {object_name} detected "
            f"on the {direction.lower()}."
        )


# ---------------------------------------
# Calculate direction
# ---------------------------------------

def calculate_direction(x_center, image_width):

    if x_center < image_width / 3:
        return "LEFT"

    elif x_center > (image_width * 2) / 3:
        return "RIGHT"

    else:
        return "CENTER"


# ---------------------------------------
# Load YOLO
# ---------------------------------------

model = YOLO("yolo11n.pt")


# ---------------------------------------
# Test image
# ---------------------------------------

image_path = "test_images/test.jpg"


# ---------------------------------------
# Simulated sensor information
# ---------------------------------------

distance = 2.0
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


print()
print("========================================")
print(" SMART GLASSES - FINAL AI OUTPUT")
print("========================================")


for result in results:

    image_width = result.orig_shape[1]

    for box in result.boxes:

        class_id = int(box.cls[0])
        confidence = float(box.conf[0])
        object_name = model.names[class_id]

        # Bounding box
        x1, y1, x2, y2 = box.xyxy[0].tolist()

        # Object center
        x_center = (x1 + x2) / 2

        # Direction
        direction = calculate_direction(
            x_center,
            image_width
        )

        # Risk
        risk = calculate_risk(
            distance,
            moving_toward
        )

        # Message
        message = create_message(
            object_name,
            distance,
            direction,
            risk
        )

        print("----------------------------------------")
        print("Object       :", object_name)
        print("Confidence   :", round(confidence, 2))
        print("Distance     :", distance, "meters")
        print("Direction    :", direction)
        print("Movement     :", "Approaching" if moving_toward else "Not approaching")
        print("Risk         :", risk)
        print("Message      :", message)
        print("----------------------------------------")


print("========================================")
print(" FINAL AI PROCESSING COMPLETED")
print("========================================")